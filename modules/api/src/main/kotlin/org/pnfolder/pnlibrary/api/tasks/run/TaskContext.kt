package org.pnfolder.pnlibrary.api.tasks.run

import org.pnfolder.pnlibrary.api.tasks.spec.TaskAction
import org.pnfolder.pnlibrary.api.tasks.spec.ScheduledTask
import org.pnfolder.pnlibrary.api.tasks.spec.TaskExecution
import org.pnfolder.pnlibrary.api.tasks.spec.TaskSpec
import org.pnfolder.pnlibrary.api.tasks.TaskHandle
import org.pnfolder.pnlibrary.api.tasks.info.TaskId
import org.pnfolder.pnlibrary.api.tasks.info.TaskSnapshot
import java.time.Duration
import java.time.Instant

/**
 * What a running task knows about itself: who it is, which run this is, and how to stop.
 *
 * It is handed to [TaskAction.run] for each invocation and is valid only during that invocation.
 *
 * ```kotlin
 * override fun run(context: TaskContext) {
 *     if (context.isFirstRun) warmUp()
 *     if (context.every(10)) saveToDatabase()
 *     if (context.lag > Duration.ofSeconds(2)) log("Late by ${context.lag}")
 *     context.cancelAfter(100)
 * }
 * ```
 */
interface TaskContext {
    /** Stable identifier of the running task. */
    val id: TaskId

    /** Optional human-readable task name. */
    val name: String?

    /** Conflict-resolution key of the task, or `null` when it has none. */
    val key: String?

    /** Lookup tags of the task. */
    val tags: Set<String>

    /** Platform execution context the invocation runs in. */
    val executionKind: TaskExecution.Kind

    /** Platform execution context of this task, handed on to follow-up work started with [later]. */
    val execution: TaskExecution

    /** Repeat interval, or `null` for a one-shot task. */
    val interval: Duration?

    /** One-based invocation number. */
    val runNumber: Long

    /** How many invocations were skipped so far because a condition rejected them or the previous run was still going. */
    val skippedCount: Long

    /** Time at which this invocation was intended to start. */
    val scheduledAt: Instant

    /** Time at which this invocation actually started. */
    val startedAt: Instant

    /** Start time of the previous invocation, or `null` on the first run. */
    val previousStartedAt: Instant?

    /** Whether the task has been cancelled, for example by an administrator while this run is going. */
    val isCancelled: Boolean

    /** The task that started this one with [spawn] or [later], or `null` for a task scheduled directly. */
    val parentId: TaskId?

    /** Cancels all future invocations of this task. */
    fun cancel()

    /** Returns a detached view of the whole task as it is right now. */
    fun snapshot(): TaskSnapshot

    /**
     * Schedules [spec] as a separate task in the same scope as this one, so it shares the owner and is cancelled
     * together with the scope. The new task is independent: cancelling this one does not stop it.
     *
     * @throws IllegalStateException when the scope is already closed
     */
    fun spawn(spec: TaskSpec): TaskHandle

    /** Schedules a task written as a class as a separate task in the same scope; see [spawn]. */
    fun spawn(task: ScheduledTask): TaskHandle = spawn(task.toSpec())

    /**
     * Runs [action] once after [delay] in this task's execution context, without building a [TaskSpec].
     *
     * ```kotlin
     * context.later(Duration.ofSeconds(5)) { player.sendMessage("Five seconds later") }
     * ```
     *
     * The follow-up is a task of its own, named after this one with the tag `subtask`.
     */
    fun later(delay: Duration, action: TaskAction): TaskHandle = later(delay, execution, action)

    /** Like [later], but in the given [execution] context, such as `TaskExecution.async()`. */
    fun later(delay: Duration, execution: TaskExecution, action: TaskAction): TaskHandle = spawn(
        TaskSpec.builder()
            .name("${name ?: id} / later")
            .tag(SUBTASK_TAG)
            .execution(execution)
            .delay(delay)
            .action(action)
            .build(),
    )

    /** Whether this is the first invocation. */
    val isFirstRun: Boolean get() = runNumber == 1L

    /** Whether the task repeats, that is, has an [interval]. */
    val isRepeating: Boolean get() = interval != null

    /** How late this invocation started compared with its plan; never negative. A big value means the server is overloaded. */
    val lag: Duration get() = Duration.between(scheduledAt, startedAt).takeUnless(Duration::isNegative) ?: Duration.ZERO

    /** How many whole runs of a repeating task were lost to the delay, such as after a server freeze; `0` for a one-shot task. */
    val missedRuns: Long
        get() = interval?.takeUnless { it.isZero }?.let { lag.toMillis() / it.toMillis() } ?: 0L

    /** Whether this invocation started more than [threshold] after its plan. */
    fun isLate(threshold: Duration): Boolean = lag > threshold

    /** How long this invocation has been running. */
    val elapsed: Duration get() = Duration.between(startedAt, Instant.now())

    /** Whether this run is every [step]-th one: true on runs [step], 2 × [step] and so on. */
    fun every(step: Long): Boolean {
        require(step > 0) { "Step must be positive" }
        return runNumber % step == 0L
    }

    /** Cancels the task when this run is the [limit]-th or later; returns whether it did. */
    fun cancelAfter(limit: Long): Boolean {
        if (runNumber < limit) return false
        cancel()
        return true
    }

    /**
     * Runs [action] [times] times, [every] apart, as a task of its own under this one; the first run is after one
     * [every]. It is cancelled together with this task, and stops by itself after the last run.
     *
     * ```kotlin
     * context.repeat(5, Duration.ofSeconds(1)) { c -> broadcast("${5 - c.runNumber}") }
     * ```
     */
    fun repeat(times: Long, every: Duration, action: TaskAction): TaskHandle {
        require(times > 0) { "Times must be positive" }
        return spawn(
            TaskSpec.builder()
                .name("${name ?: id} / repeat")
                .tag(SUBTASK_TAG)
                .execution(execution)
                .delay(every)
                .interval(every)
                .cancelWithParent(true)
                .action { child ->
                    action.run(child)
                    child.cancelAfter(times)
                }
                .build(),
        )
    }

    /** Constants of task contexts. */
    companion object {
        /** Tag put on every task started with [later]. */
        const val SUBTASK_TAG: String = "subtask"
    }
}
