package org.pnfolder.pnlibrary.core.tasks

import org.pnfolder.pnlibrary.api.plugin.PluginId
import org.pnfolder.pnlibrary.api.tasks.TaskConflictPolicy
import org.pnfolder.pnlibrary.api.tasks.TaskHandle
import org.pnfolder.pnlibrary.api.tasks.TaskId
import org.pnfolder.pnlibrary.api.tasks.TaskQuery
import org.pnfolder.pnlibrary.api.tasks.TaskScope
import org.pnfolder.pnlibrary.api.tasks.TaskServiceSettings
import org.pnfolder.pnlibrary.api.tasks.TaskSnapshot
import org.pnfolder.pnlibrary.api.tasks.TaskSpec
import org.pnfolder.pnlibrary.core.lifecycle.ResourceCleanup
import org.pnfolder.pnlibrary.core.lifecycle.TaskControl
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskAdapter
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskRequest
import java.util.Collections
import java.util.IdentityHashMap
import java.util.LinkedHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** Schedules tasks through the platform adapter, grouped into per-plugin scopes and kept in a bounded history. */
internal class TaskServiceImpl(
    private val adapter: PlatformTaskAdapter,
    private val settings: TaskServiceSettings = TaskServiceSettings(),
    private val errorLogger: (Any, String, Throwable) -> Unit = { _, _, _ -> },
    private val store: TaskHistoryStore? = null,
) : TaskService {
    private val lock = Any()
    private val scopes = IdentityHashMap<Any, MutableMap<PluginId, Scope>>()
    private val active = LinkedHashMap<TaskId, ManagedTask>()
    private val history = LinkedHashMap<TaskId, Archived>()
    private val closed = AtomicBoolean(false)
    private var lastSaved = 0L

    init {
        // Tasks of earlier runs have no owner object any more, so only the global list shows them.
        store?.load()?.takeLast(settings.historyCapacity.coerceAtLeast(0))?.forEach { history[it.id] = Archived(Any(), Any(), it) }
    }

    override fun scope(owner: Any): TaskScope {
        return scope(owner, PluginId.of(owner.javaClass.simpleName.ifBlank { "owner" }))
    }

    override fun scope(owner: Any, scopeId: PluginId): TaskScope = synchronized(scopes) {
        check(!closed.get()) { "TaskService is closed" }
        scopes.getOrPut(owner) { linkedMapOf() }.getOrPut(scopeId) { Scope(owner, scopeId) }
    }

    override fun get(id: TaskId): TaskHandle? = synchronized(lock) { active[id] }
    override fun query(query: TaskQuery): List<TaskSnapshot> = synchronized(lock) {
        immutableSnapshots((active.values.map { it.snapshot() } + history.values.map { it.snapshot })
            .filter { TaskQueryMatcher.matches(it, query) })
    }
    override fun query(owner: Any, query: TaskQuery): List<TaskSnapshot> = snapshots(owner, query)

    override fun cancelByAdmin(id: TaskId): TaskControl {
        val task = synchronized(lock) { active[id] } ?: return TaskControl.UNAVAILABLE
        if (!task.spec.cancellable) return TaskControl.FORBIDDEN
        return if (task.cancelIfActive()) TaskControl.DONE else TaskControl.UNAVAILABLE
    }

    override fun runNow(id: TaskId): TaskControl {
        val task = synchronized(lock) { active[id] } ?: return TaskControl.UNAVAILABLE
        if (!task.spec.runnableNow) return TaskControl.FORBIDDEN
        return if (task.runNow()) TaskControl.DONE else TaskControl.UNAVAILABLE
    }
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
            { saveHistory() },
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
                val managed = newTask(owner, scopeToken, spec)
                active[managed.id] = managed
                val native = try {
                    adapter.schedule(PlatformTaskRequest(
                        spec.execution.kind, spec.execution.target, spec.delay, spec.interval, Runnable { managed.invoke() },
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

    private fun newTask(owner: Any, scopeToken: Any, spec: TaskSpec) =
        ManagedTask(owner, scopeToken, spec, adapter, errorLogger, ::archive)

    /** Moves a finished task from the active list to the history; does nothing if it was already moved. */
    private fun archive(task: ManagedTask) {
        val archived = synchronized(lock) {
            if (active.remove(task.id) == null) return
            if (settings.historyCapacity > 0) {
                history[task.id] = Archived(task.owner, task.scopeToken, task.snapshot())
                while (history.size > settings.historyCapacity) history.remove(history.keys.first())
                true
            } else {
                false
            }
        }
        if (archived) saveHistoryIfDue()
    }

    /** Writes the history at most every [SAVE_EVERY_MILLIS], so a plugin that runs many short tasks does not hammer the disk. */
    private fun saveHistoryIfDue() {
        val now = System.currentTimeMillis()
        val due = synchronized(lock) { (now - lastSaved >= SAVE_EVERY_MILLIS).also { if (it) lastSaved = now } }
        if (due) saveHistory()
    }

    private fun saveHistory() {
        val snapshots = synchronized(lock) { history.values.map { it.snapshot } }
        store?.save(snapshots)
    }

    private fun immutableSnapshots(values: Collection<TaskSnapshot>): List<TaskSnapshot> =
        Collections.unmodifiableList(ArrayList(values))
    private data class Archived(val owner: Any, val scopeToken: Any, val snapshot: TaskSnapshot)

    private companion object {
        const val SAVE_EVERY_MILLIS = 30_000L
    }
}
