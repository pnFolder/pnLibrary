package org.pnfolder.pnlibrary.core.tasks

import org.pnfolder.pnlibrary.api.tasks.TaskHandle
import org.pnfolder.pnlibrary.api.tasks.TaskId
import org.pnfolder.pnlibrary.api.tasks.TaskQuery
import org.pnfolder.pnlibrary.api.tasks.TaskScope
import org.pnfolder.pnlibrary.api.tasks.TaskSnapshot

/**
 * Cross-platform scheduling service with owner-scoped resource management.
 *
 * Repeated calls to [scope] with the same owner object return the same scope. Owner identity is
 * used instead of [Any.equals], so distinct but equal objects receive distinct scopes. Platform
 * adapters decide the exact global and entity execution model, including Folia region dispatch.
 * Scope creation, lookup, queries, cancellation, and shutdown are safe from arbitrary threads.
 * Query results are detached immutable snapshots; task actions run in the execution context
 * selected by [TaskSpec.execution], not necessarily on the calling thread.
 */
internal interface TaskService : AutoCloseable {
    /** Returns the active scope associated with [owner], creating it when necessary. */
    fun scope(owner: Any): TaskScope

    /** Returns the task identified by [id] across all active scopes. */
    fun get(id: TaskId): TaskHandle? = null

    /** Returns the task identified by [id] or fails when unavailable. */
    fun require(id: TaskId): TaskHandle = get(id) ?: error("Task $id is unavailable")

    /** Returns detached snapshots across all scopes that match [query]. */
    fun query(query: TaskQuery = TaskQuery.all()): List<TaskSnapshot> = emptyList()

    /** Returns detached snapshots belonging to [owner] that match [query]. */
    fun query(owner: Any, query: TaskQuery = TaskQuery.all()): List<TaskSnapshot> = emptyList()

    /** Cancels the task identified by [id] and reports whether it was active. */
    fun cancel(id: TaskId): Boolean = get(id)?.cancelIfActive() ?: false

    /** Closes and removes [owner]'s scope when one exists. */
    fun close(owner: Any)

    /** Closes all scopes and stops the background scheduler. This operation is idempotent. */
    override fun close()
}
