package ru.privatenull.pnlibrary.core.tasks

import ru.privatenull.pnlibrary.api.plugin.PluginId
import ru.privatenull.pnlibrary.api.tasks.TaskConflictPolicy
import ru.privatenull.pnlibrary.api.tasks.TaskContext
import ru.privatenull.pnlibrary.api.tasks.TaskHandle
import ru.privatenull.pnlibrary.api.tasks.TaskId
import ru.privatenull.pnlibrary.api.tasks.TaskQuery
import ru.privatenull.pnlibrary.api.tasks.TaskScope
import ru.privatenull.pnlibrary.api.tasks.TaskServiceSettings
import ru.privatenull.pnlibrary.api.tasks.TaskSnapshot
import ru.privatenull.pnlibrary.api.tasks.TaskSpec
import ru.privatenull.pnlibrary.api.tasks.TaskStatus
import ru.privatenull.pnlibrary.core.lifecycle.ResourceCleanup
import ru.privatenull.pnlibrary.spi.tasks.PlatformTaskAdapter
import ru.privatenull.pnlibrary.spi.tasks.PlatformTaskHandle
import ru.privatenull.pnlibrary.spi.tasks.PlatformTaskRequest
import java.time.Instant
import java.util.Collections
import java.util.IdentityHashMap
import java.util.LinkedHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

internal class TaskServiceImpl(
    private val adapter: PlatformTaskAdapter,
    private val settings: TaskServiceSettings = TaskServiceSettings(),
    private val errorLogger: (Any, String, Throwable) -> Unit = { _, _, _ -> },
) : TaskService {
    private val lock = Any()
    private val scopes = IdentityHashMap<Any, MutableMap<PluginId, Scope>>()
    private val active = LinkedHashMap<TaskId, ManagedTask>()
    private val history = LinkedHashMap<TaskId, Archived>()
    private val closed = AtomicBoolean(false)

    override fun scope(owner: Any): TaskScope {
        return scope(owner, PluginId.of(owner.javaClass.simpleName.ifBlank { "owner" }))
    }

    internal fun scope(owner: Any, scopeId: PluginId): TaskScope = synchronized(scopes) {
        check(!closed.get()) { "TaskService is closed" }
        scopes.getOrPut(owner) { linkedMapOf() }.getOrPut(scopeId) { Scope(owner, scopeId) }
    }

    override fun get(id: TaskId): TaskHandle? = synchronized(lock) { active[id] }
    override fun query(query: TaskQuery): List<TaskSnapshot> = synchronized(lock) {
        immutableSnapshots((active.values.map { it.snapshot() } + history.values.map { it.snapshot })
            .filter { TaskQueryMatcher.matches(it, query) })
    }
    override fun query(owner: Any, query: TaskQuery): List<TaskSnapshot> = snapshots(owner, query)
    override fun close(owner: Any) {
        val current = synchronized(scopes) { scopes.remove(owner)?.values?.toList().orEmpty() }
        ResourceCleanup.closeAll(*current.map { scope -> ({ scope.cancelAll() }) }.toTypedArray())
    }
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        val current = synchronized(scopes) {
            scopes.values.flatMap { it.values }.also { scopes.clear() }
        }
        ResourceCleanup.closeAll(
            *current.map { scope -> ({ scope.cancelAll() }) }.toTypedArray(),
            { adapter.close() },
        )
    }

    private fun snapshots(owner: Any, query: TaskQuery) = synchronized(lock) {
        immutableSnapshots((active.values.filter { it.owner === owner }.map { it.snapshot() } +
            history.values.filter { it.owner === owner }.map { it.snapshot })
            .filter { TaskQueryMatcher.matches(it, query) })
    }

    private inner class Scope(override val owner: Any, private val scopeId: PluginId) : TaskScope {
        private val scopeToken = Any()
        private val scopeClosed = AtomicBoolean(false)

        override fun schedule(spec: TaskSpec): TaskHandle {
            check(!scopeClosed.get() && !closed.get()) { "TaskScope is closed" }
            synchronized(lock) {
                val existing = spec.key?.let { key ->
                    active.values.firstOrNull { it.scopeToken === scopeToken && it.spec.key == key }
                }
                if (existing != null) when (spec.conflictPolicy) {
                    TaskConflictPolicy.REJECT -> error("Task key '${spec.key}' is already active for this owner")
                    TaskConflictPolicy.KEEP_EXISTING -> return existing
                    TaskConflictPolicy.REPLACE -> existing.cancel()
                }
                val managed = ManagedTask(owner, scopeToken, spec)
                active[managed.id] = managed
                val native = try {
                    adapter.schedule(PlatformTaskRequest(
                        spec.execution.kind, spec.execution.target, spec.delay, spec.interval, Runnable(managed::invoke),
                        owner = owner,
                    ))
                } catch (error: Throwable) {
                    active.remove(managed.id)
                    throw error
                }
                if (scopeClosed.get() || closed.get()) {
                    active.remove(managed.id)
                    native.cancel()
                    error("TaskScope is closed")
                }
                managed.attach(native)
                return managed
            }
        }

        override fun get(id: TaskId): TaskHandle? = synchronized(lock) {
            active[id]?.takeIf { it.scopeToken === scopeToken }
        }
        override fun getByKey(key: String): TaskHandle? = synchronized(lock) {
            active.values.firstOrNull { it.scopeToken === scopeToken && it.spec.key == key }
        }
        override fun query(query: TaskQuery): List<TaskSnapshot> = synchronized(lock) {
            immutableSnapshots((active.values.filter { it.scopeToken === scopeToken }.map { it.snapshot() } +
                history.values.filter { it.scopeToken === scopeToken }.map { it.snapshot })
                .filter { TaskQueryMatcher.matches(it, query) })
        }

        override fun cancelAll() {
            if (!scopeClosed.compareAndSet(false, true)) return
            val pending = synchronized(lock) { active.values.filter { it.scopeToken === scopeToken }.toList() }
            try {
                ResourceCleanup.closeAll(*pending.map { task -> ({ task.cancel() }) }.toTypedArray())
            } finally {
                synchronized(scopes) {
                    scopes[owner]?.let { owned ->
                        owned.remove(scopeId, this)
                        if (owned.isEmpty()) scopes.remove(owner)
                    }
                }
            }
        }
    }

    private inner class ManagedTask(val owner: Any, val scopeToken: Any, val spec: TaskSpec) : TaskHandle {
        override val id = TaskId.random()
        private val state = AtomicReference(TaskStatus.SCHEDULED)
        private val running = AtomicBoolean(false)
        private val runs = AtomicLong()
        private val skipped = AtomicLong()
        private val cancelled = AtomicBoolean(false)
        private val native = AtomicReference<PlatformTaskHandle?>()
        private val created = Instant.now()
        @Volatile
        private var nextRun: Instant? = created.plus(spec.delay)

        @Volatile
        private var started: Instant? = null

        @Volatile
        private var completed: Instant? = null

        @Volatile
        private var failure: String? = null
        override val status get() = state.get()
        override val isCancelled get() = cancelled.get()

        fun attach(handle: PlatformTaskHandle) {
            native.set(handle)
            if (cancelled.get() || status == TaskStatus.COMPLETED || status == TaskStatus.FAILED) {
                handle.cancel()
            }
        }

        fun invoke() {
            if (cancelled.get()) return
            if (!running.compareAndSet(false, true)) {
                skipped.incrementAndGet()
                return
            }
            try {
                nextRun = null
                if (spec.cancellationConditions.any { it.asBoolean }) {
                    cancel()
                    return
                }
                if (spec.conditions.any { !it.asBoolean }) {
                    skipped.incrementAndGet()
                    if (spec.interval == null) {
                        complete()
                    } else {
                        nextRun = Instant.now().plus(spec.interval)
                    }
                    return
                }
                state.set(TaskStatus.RUNNING)
                val runStartedAt = Instant.now()
                started = runStartedAt
                val number = runs.incrementAndGet()
                spec.action.run(object : TaskContext {
                    override val id = this@ManagedTask.id
                    override val name = spec.name
                    override val runNumber = number
                    override val scheduledAt = created.plus(spec.delay)
                    override val startedAt = runStartedAt
                    override fun cancel() = this@ManagedTask.cancel()
                })
                if (!cancelled.get()) {
                    if (spec.interval == null) {
                        complete()
                    } else {
                        state.set(TaskStatus.SCHEDULED)
                        nextRun = Instant.now().plus(spec.interval)
                    }
                }
            } catch (error: Throwable) {
                failure = "${error.javaClass.simpleName}: ${error.message.orEmpty()}"
                state.set(TaskStatus.FAILED)
                completed = Instant.now()
                ResourceCleanup.suppressInto(
                    error,
                    { native.get()?.cancel() },
                    { errorLogger(owner, "[pnLibrary/tasks] Task ${spec.name ?: id} failed", error) },
                )
                terminal()
            } finally {
                running.set(false)
            }
        }

        private fun complete() {
            state.set(TaskStatus.COMPLETED)
            completed = Instant.now()
            terminal()
        }

        override fun cancel() { cancelIfActive() }

        override fun cancelIfActive(): Boolean {
            if (!cancelled.compareAndSet(false, true)) return false
            state.set(TaskStatus.CANCELLED)
            completed = Instant.now()
            try {
                native.get()?.cancel()
            } finally {
                terminal()
            }
            return true
        }

        override fun snapshot() = TaskSnapshot(
            id = id,
            name = spec.name,
            key = spec.key,
            ownerName = ownerName(owner),
            executionKind = spec.execution.kind,
            status = status,
            tags = spec.tags,
            createdAt = created,
            nextRunAt = nextRun,
            runCount = runs.get(),
            skippedCount = skipped.get(),
            lastStartedAt = started,
            lastCompletedAt = completed,
            lastFailure = failure,
        )

        private fun terminal() = synchronized(lock) {
            if (active.remove(id) == null) return@synchronized
            if (settings.historyCapacity > 0) {
                history[id] = Archived(owner, scopeToken, snapshot())
                while (history.size > settings.historyCapacity) history.remove(history.keys.first())
            }
        }
    }

    private fun ownerName(owner: Any) = owner.javaClass.simpleName.ifBlank { owner.toString() }
    private fun immutableSnapshots(values: Collection<TaskSnapshot>): List<TaskSnapshot> =
        Collections.unmodifiableList(ArrayList(values))
    private data class Archived(val owner: Any, val scopeToken: Any, val snapshot: TaskSnapshot)
}
