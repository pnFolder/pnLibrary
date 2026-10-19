package org.pnfolder.pnlibrary.api.tasks

/**
 * Cancellation handle for one scheduled action.
 *
 * Cancellation is idempotent. It prevents a pending invocation from starting, but does not
 * interrupt an invocation that is already running. Closing the handle is equivalent to [cancel].
 */
interface TaskHandle : AutoCloseable {
    /** Stable identifier of the associated task. */
    val id: TaskId
    /** Current task lifecycle state. */
    val status: TaskStatus
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

    /** Returns a detached snapshot of this task's current state. */
    fun snapshot(): TaskSnapshot

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
 * val tasks = context.tasks
 * tasks.schedule(TaskSpec.builder()
 *     .interval(Duration.ofMinutes(1))
 *     .action { refreshOnlinePlayers() }
 *     .build())
 * tasks.close()
 * ```
 */
interface TaskScope : AutoCloseable {
    /** Object whose lifecycle owns every task in this scope. */
    val owner: Any

    /** Schedules [spec] inside this owner-bound scope. */
    fun schedule(spec: TaskSpec): TaskHandle

    /** Returns the task identified by [id], or `null` when it is unavailable. */
    fun get(id: TaskId): TaskHandle?

    /** Returns the task identified by [id] or fails when unavailable. */
    fun require(id: TaskId): TaskHandle = get(id) ?: error("Task $id is unavailable in this scope")

    /** Returns the live task owning [key], or `null` when absent. */
    fun getByKey(key: String): TaskHandle?

    /** Returns detached snapshots of tasks in this scope that match [query]. */
    fun query(query: TaskQuery = TaskQuery.all()): List<TaskSnapshot>

    /** Cancels the task identified by [id] and reports whether it was active. */
    fun cancel(id: TaskId): Boolean = get(id)?.cancelIfActive() ?: false

    /** Cancels every task and permanently closes this scope. */
    fun cancelAll()

    /** Cancels every task and permanently closes this scope. */
    override fun close() = cancelAll()
}
