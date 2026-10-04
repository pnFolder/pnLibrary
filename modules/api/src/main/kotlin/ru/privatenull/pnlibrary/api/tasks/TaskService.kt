package ru.privatenull.pnlibrary.api.tasks

import java.time.Duration
import java.util.function.Consumer
import java.util.function.Supplier

/**
 * Cancellation handle for one scheduled action.
 *
 * Cancellation is idempotent. It prevents a pending invocation from starting, but does not
 * interrupt an invocation that is already running. Closing the handle is equivalent to [cancel].
 */
interface TaskHandle : AutoCloseable {
    /** Stable identifier of the associated task. */
    val id: TaskId get() = TaskId("legacy-${System.identityHashCode(this)}")
    /** Current task lifecycle state. */
    val status: TaskStatus get() = if (isCancelled) TaskStatus.CANCELLED else TaskStatus.SCHEDULED
    /** Whether this handle has been cancelled. */
    val isCancelled: Boolean

    /** Cancels future execution of the associated action. */
    fun cancel()

    /** Cancels this task when active and reports whether cancellation was requested. */
    fun cancelIfActive(): Boolean {
        if (isCancelled) return false
        cancel()
        return true
    }

    /** Returns a detached current snapshot when supported by the implementation. */
    fun snapshot(): TaskSnapshot = throw UnsupportedOperationException("Task snapshots are not supported by this handle")

    /** Cancels future execution of the associated action. */
    override fun close() = cancel()
}

/**
 * Owner-bound group of tasks that are cancelled together.
 *
 * A scope is intended to live for exactly as long as its plugin or subsystem. Calling [close]
 * cancels every pending and repeating task in the scope and prevents new tasks from being added.
 * Durations must not be negative; repeat intervals must additionally be greater than zero.
 *
 * Example:
 * ```kotlin
 * val tasks = library.tasks.scope(plugin)
 * tasks.repeat(Duration.ZERO, Duration.ofMinutes(1)) {
 *     refreshOnlinePlayers()
 * }
 * tasks.close()
 * ```
 */
interface TaskScope : AutoCloseable {
    /** Object whose lifecycle owns every task in this scope. */
    val owner: Any

    /** Schedules [spec] inside this owner-bound scope. */
    fun schedule(spec: TaskSpec): TaskHandle =
        throw UnsupportedOperationException("Unified task scheduling is not supported by this scope")

    @Suppress("DEPRECATION")
    /** Returns the task identified by [id], or `null` when it is unavailable. */
    fun get(id: TaskId): TaskHandle? = find(id)

    /** Returns the task identified by [id] or fails when unavailable. */
    fun require(id: TaskId): TaskHandle = get(id) ?: error("Task $id is unavailable in this scope")

    @Suppress("DEPRECATION")
    /** Returns the live task owning [key], or `null` when absent. */
    fun getByKey(key: String): TaskHandle? = findByKey(key)

    /** Legacy identifier lookup retained for source compatibility. */
    @Deprecated("Use get(id)", ReplaceWith("get(id)"))
    fun find(id: TaskId): TaskHandle? = null

    /** Legacy key lookup retained for source compatibility. */
    @Deprecated("Use getByKey(key)", ReplaceWith("getByKey(key)"))
    fun findByKey(key: String): TaskHandle? = null

    /** Returns detached snapshots of tasks in this scope that match [query]. */
    fun query(query: TaskQuery = TaskQuery.all()): List<TaskSnapshot> = emptyList()

    /** Cancels the task identified by [id] and reports whether it was active. */
    fun cancel(id: TaskId): Boolean = get(id)?.cancelIfActive() ?: false

    /** Schedules [task] in the platform's global execution context. */
    @Deprecated("Use schedule(TaskSpec)")
    fun global(task: Runnable): TaskHandle

    /** Runs [task] on pnLibrary's background executor. */
    @Deprecated("Use schedule(TaskSpec)")
    fun async(task: Runnable): TaskHandle

    /** Schedules [task] in the platform execution context associated with [recipient]. */
    @Deprecated("Use schedule(TaskSpec)")
    fun entity(recipient: Any, task: Runnable): TaskHandle

    /** Schedules [task] in the global context after [delay]. */
    @Deprecated("Use schedule(TaskSpec)")
    fun later(delay: Duration, task: Runnable): TaskHandle

    /** Schedules [task] in [recipient]'s execution context after [delay]. */
    @Deprecated("Use schedule(TaskSpec)")
    fun laterEntity(recipient: Any, delay: Duration, task: Runnable): TaskHandle

    /** Repeats [task] in the global context, first after [delay], then every [interval]. */
    @Deprecated("Use schedule(TaskSpec)")
    fun repeat(delay: Duration, interval: Duration, task: Runnable): TaskHandle

    /** Repeats [task] in [recipient]'s execution context using the supplied timing. */
    @Deprecated("Use schedule(TaskSpec)")
    fun repeatEntity(recipient: Any, delay: Duration, interval: Duration, task: Runnable): TaskHandle

    /** Repeats [task] on pnLibrary's background executor using the supplied timing. */
    @Deprecated("Use schedule(TaskSpec)")
    fun repeatAsync(delay: Duration, interval: Duration, task: Runnable): TaskHandle

    /**
     * Computes [work] asynchronously and dispatches its result to the global context.
     *
     * Exactly one of [success] or [failure] is invoked unless the returned handle or this scope is
     * cancelled first. Exceptions thrown by either continuation are reported by the task logger.
     */
    @Deprecated("Use schedule(TaskSpec)")
    fun <T> asyncThen(
        work: Supplier<T>,
        success: Consumer<T>,
        failure: Consumer<Throwable>,
    ): TaskHandle

    /** Cancels every task and permanently closes this scope. */
    fun cancelAll()

    /** Cancels every task and permanently closes this scope. */
    override fun close() = cancelAll()
}

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
interface TaskService : AutoCloseable {
    /** Returns the active scope associated with [owner], creating it when necessary. */
    fun scope(owner: Any): TaskScope

    @Suppress("DEPRECATION")
    /** Returns the task identified by [id] across all active scopes. */
    fun get(id: TaskId): TaskHandle? = find(id)

    /** Returns the task identified by [id] or fails when unavailable. */
    fun require(id: TaskId): TaskHandle = get(id) ?: error("Task $id is unavailable")

    /** Legacy identifier lookup retained for source compatibility. */
    @Deprecated("Use get(id)", ReplaceWith("get(id)"))
    fun find(id: TaskId): TaskHandle? = null

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
