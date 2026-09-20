@file:Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")

package ru.privatenull.pnlibrary.core.tasks

import ru.privatenull.pnlibrary.api.logging.LogLevel
import ru.privatenull.pnlibrary.api.tasks.*
import ru.privatenull.pnlibrary.spi.platform.PlatformAdapter
import ru.privatenull.pnlibrary.spi.tasks.*
import java.time.Duration
import java.time.Instant
import java.util.Collections
import java.util.IdentityHashMap
import java.util.LinkedHashMap
import java.util.concurrent.atomic.*
import java.util.function.Consumer
import java.util.function.Supplier

internal class TaskServiceImpl(
    private val adapter: PlatformTaskAdapter,
    private val settings: TaskServiceSettings = TaskServiceSettings(),
    private val errorLogger: (Any, String, Throwable) -> Unit = { _, _, _ -> },
) : TaskService {
    constructor(platform: PlatformAdapter, errorLogger: (Any, String, Throwable) -> Unit = { owner, message, error ->
        platform.log(owner, LogLevel.ERROR, message, error)
    }) : this(platform.taskAdapter, TaskServiceSettings(), errorLogger)

    private val lock = Any()
    private val scopes = Collections.synchronizedMap(IdentityHashMap<Any, Scope>())
    private val active = LinkedHashMap<TaskId, ManagedTask>()
    private val history = LinkedHashMap<TaskId, Archived>()
    private val closed = AtomicBoolean(false)

    override fun scope(owner: Any): TaskScope {
        check(!closed.get()) { "TaskService is closed" }
        return synchronized(scopes) { scopes.getOrPut(owner) { Scope(owner) } }
    }

    override fun find(id: TaskId): TaskHandle? = synchronized(lock) { active[id] }
    override fun query(query: TaskQuery): List<TaskSnapshot> = synchronized(lock) {
        (active.values.map { it.snapshot() } + history.values.map { it.snapshot }).filter { matches(it, query) }
    }
    override fun query(owner: Any, query: TaskQuery): List<TaskSnapshot> = snapshots(owner, query)
    override fun close(owner: Any) { synchronized(scopes) { scopes.remove(owner) }?.cancelAll() }
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        synchronized(scopes) { scopes.values.toList().also { scopes.clear() } }.forEach { it.cancelAll() }
        adapter.close()
    }

    private fun snapshots(owner: Any, query: TaskQuery) = synchronized(lock) {
        (active.values.filter { it.owner === owner }.map { it.snapshot() } +
            history.values.filter { it.owner === owner }.map { it.snapshot }).filter { matches(it, query) }
    }

    private fun matches(value: TaskSnapshot, query: TaskQuery): Boolean =
        (query.id == null || value.id == query.id) &&
            (query.key == null || value.key == query.key) &&
            (query.nameContains == null || value.name?.contains(query.nameContains!!, ignoreCase = true) == true) &&
            (query.statuses.isEmpty() || value.status in query.statuses) &&
            (query.executionKinds.isEmpty() || value.executionKind in query.executionKinds) &&
            (query.tags.isEmpty() || value.tags.containsAll(query.tags))

    private inner class Scope(override val owner: Any) : TaskScope {
        private val scopeClosed = AtomicBoolean(false)

        override fun schedule(spec: TaskSpec): TaskHandle {
            check(!scopeClosed.get() && !closed.get()) { "TaskScope is closed" }
            synchronized(lock) {
                val existing = spec.key?.let { key -> active.values.firstOrNull { it.owner === owner && it.spec.key == key } }
                if (existing != null) when (spec.conflictPolicy) {
                    TaskConflictPolicy.REJECT -> error("Task key '${spec.key}' is already active for this owner")
                    TaskConflictPolicy.KEEP_EXISTING -> return existing
                    TaskConflictPolicy.REPLACE -> existing.cancel()
                }
                val managed = ManagedTask(owner, spec)
                active[managed.id] = managed
                val native = try {
                    adapter.schedule(PlatformTaskRequest(
                        spec.execution.kind, spec.execution.target, spec.delay, spec.interval, Runnable(managed::invoke),
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

        override fun find(id: TaskId): TaskHandle? = synchronized(lock) { active[id]?.takeIf { it.owner === owner } }
        override fun findByKey(key: String): TaskHandle? = synchronized(lock) {
            active.values.firstOrNull { it.owner === owner && it.spec.key == key }
        }
        override fun query(query: TaskQuery): List<TaskSnapshot> = snapshots(owner, query)

        override fun global(task: Runnable) = schedule(simple(TaskExecution.global(), action = task))
        override fun async(task: Runnable) = schedule(simple(TaskExecution.async(), action = task))
        override fun entity(recipient: Any, task: Runnable) = schedule(simple(TaskExecution.entity(recipient), action = task))
        override fun later(delay: Duration, task: Runnable) = schedule(simple(TaskExecution.global(), delay, action = task))
        override fun laterEntity(recipient: Any, delay: Duration, task: Runnable) =
            schedule(simple(TaskExecution.entity(recipient), delay, action = task))
        override fun repeat(delay: Duration, interval: Duration, task: Runnable) =
            schedule(simple(TaskExecution.global(), delay, interval, task))
        override fun repeatEntity(recipient: Any, delay: Duration, interval: Duration, task: Runnable) =
            schedule(simple(TaskExecution.entity(recipient), delay, interval, task))
        override fun repeatAsync(delay: Duration, interval: Duration, task: Runnable) =
            schedule(simple(TaskExecution.async(), delay, interval, task))
        override fun <T> asyncThen(work: Supplier<T>, success: Consumer<T>, failure: Consumer<Throwable>): TaskHandle =
            schedule(TaskSpec.builder().execution(TaskExecution.async()).action {
                runCatching(work::get).fold(
                    { value -> global(Runnable { success.accept(value) }) },
                    { error -> global(Runnable { failure.accept(error) }) },
                )
            }.build())

        override fun cancelAll() {
            if (!scopeClosed.compareAndSet(false, true)) return
            synchronized(lock) { active.values.filter { it.owner === owner }.toList() }.forEach { it.cancel() }
            synchronized(scopes) { if (scopes[owner] === this) scopes.remove(owner) }
        }
    }

    private fun simple(execution: TaskExecution, delay: Duration = Duration.ZERO,
        interval: Duration? = null, action: Runnable): TaskSpec =
        TaskSpec.builder().execution(execution).delay(delay).interval(interval).action { action.run() }.build()

    private inner class ManagedTask(val owner: Any, val spec: TaskSpec) : TaskHandle {
        override val id = TaskId.random()
        private val state = AtomicReference(TaskStatus.SCHEDULED)
        private val running = AtomicBoolean(false)
        private val runs = AtomicLong()
        private val skipped = AtomicLong()
        private val cancelled = AtomicBoolean(false)
        private val native = AtomicReference<PlatformTaskHandle?>()
        private val created = Instant.now()
        @Volatile private var nextRun: Instant? = created.plus(spec.delay)
        @Volatile private var started: Instant? = null
        @Volatile private var completed: Instant? = null
        @Volatile private var failure: String? = null
        override val status get() = state.get()
        override val isCancelled get() = cancelled.get()

        fun attach(handle: PlatformTaskHandle) {
            native.set(handle)
            if (cancelled.get() || status == TaskStatus.COMPLETED || status == TaskStatus.FAILED) handle.cancel()
        }
        fun invoke() {
            if (cancelled.get()) return
            if (!running.compareAndSet(false, true)) { skipped.incrementAndGet(); return }
            try {
                nextRun = null
                if (spec.cancellationConditions.any { it.asBoolean }) { cancel(); return }
                if (spec.conditions.any { !it.asBoolean }) {
                    skipped.incrementAndGet()
                    if (spec.interval == null) complete() else nextRun = Instant.now().plus(spec.interval)
                    return
                }
                state.set(TaskStatus.RUNNING); started = Instant.now()
                val number = runs.incrementAndGet()
                spec.action.run(object : TaskContext {
                    override val id = this@ManagedTask.id
                    override val name = spec.name
                    override val runNumber = number
                    override val scheduledAt = created.plus(spec.delay)
                    override val startedAt = started!!
                    override fun cancel() = this@ManagedTask.cancel()
                })
                if (!cancelled.get()) if (spec.interval == null) complete() else {
                    state.set(TaskStatus.SCHEDULED); nextRun = Instant.now().plus(spec.interval)
                }
            } catch (error: Throwable) {
                failure = "${error.javaClass.simpleName}: ${error.message.orEmpty()}"
                state.set(TaskStatus.FAILED); native.get()?.cancel()
                errorLogger(owner, "[pnLibrary/tasks] Task ${spec.name ?: id} failed", error)
                terminal()
            } finally { running.set(false) }
        }
        private fun complete() { state.set(TaskStatus.COMPLETED); completed = Instant.now(); terminal() }
        override fun cancel() { cancelIfActive() }
        override fun cancelIfActive(): Boolean {
            if (!cancelled.compareAndSet(false, true)) return false
            state.set(TaskStatus.CANCELLED); completed = Instant.now(); native.get()?.cancel(); terminal(); return true
        }
        override fun snapshot() = TaskSnapshot(id, spec.name, spec.key, ownerName(owner), spec.execution.kind,
            status, spec.tags, created, nextRun, runs.get(), skipped.get(), started, completed, failure)
        private fun terminal() = synchronized(lock) {
            if (active.remove(id) == null) return@synchronized
            if (settings.historyCapacity > 0) {
                history[id] = Archived(owner, snapshot())
                while (history.size > settings.historyCapacity) history.remove(history.keys.first())
            }
        }
    }

    private fun ownerName(owner: Any) = owner.javaClass.simpleName.ifBlank { owner.toString() }
    private data class Archived(val owner: Any, val snapshot: TaskSnapshot)
}
