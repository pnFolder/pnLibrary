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
    /** Whether this handle has been cancelled. */
    val isCancelled: Boolean

    /** Cancels future execution of the associated action. */
    fun cancel()

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

    /** Schedules [task] in the platform's global execution context. */
    fun global(task: Runnable): TaskHandle

    /** Runs [task] on pnLibrary's background executor. */
    fun async(task: Runnable): TaskHandle

    /** Schedules [task] in the platform execution context associated with [recipient]. */
    fun entity(recipient: Any, task: Runnable): TaskHandle

    /** Schedules [task] in the global context after [delay]. */
    fun later(delay: Duration, task: Runnable): TaskHandle

    /** Schedules [task] in [recipient]'s execution context after [delay]. */
    fun laterEntity(recipient: Any, delay: Duration, task: Runnable): TaskHandle

    /** Repeats [task] in the global context, first after [delay], then every [interval]. */
    fun repeat(delay: Duration, interval: Duration, task: Runnable): TaskHandle

    /** Repeats [task] in [recipient]'s execution context using the supplied timing. */
    fun repeatEntity(recipient: Any, delay: Duration, interval: Duration, task: Runnable): TaskHandle

    /** Repeats [task] on pnLibrary's background executor using the supplied timing. */
    fun repeatAsync(delay: Duration, interval: Duration, task: Runnable): TaskHandle

    /**
     * Computes [work] asynchronously and dispatches its result to the global context.
     *
     * Exactly one of [success] or [failure] is invoked unless the returned handle or this scope is
     * cancelled first. Exceptions thrown by either continuation are reported by the task logger.
     */
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
 */
interface TaskService : AutoCloseable {
    /** Returns the active scope associated with [owner], creating it when necessary. */
    fun scope(owner: Any): TaskScope

    /** Closes and removes [owner]'s scope when one exists. */
    fun close(owner: Any)

    /** Closes all scopes and stops the background scheduler. This operation is idempotent. */
    override fun close()
}
