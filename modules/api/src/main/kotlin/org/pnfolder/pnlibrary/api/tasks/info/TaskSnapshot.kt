package org.pnfolder.pnlibrary.api.tasks.info

import org.pnfolder.pnlibrary.api.tasks.spec.TaskExecution
import java.time.Duration
import java.time.Instant

/**
 * Immutable detached view of a live or retained task.
 *
 * @property id stable task identifier
 * @property name optional human-readable task name
 * @property key optional conflict-resolution key
 * @property ownerName diagnostic name of the owning object
 * @property executionKind selected execution context
 * @property status current or terminal lifecycle state
 * @property tags immutable application-defined tags
 * @property createdAt time at which the task was registered
 * @property nextRunAt next planned invocation, or `null` when none remains
 * @property runCount number of invocations that started
 * @property skippedCount number of invocations skipped because a condition rejected them
 * @property lastStartedAt most recent invocation start time
 * @property lastCompletedAt most recent successful completion time
 * @property lastFailure rendered most recent failure, when one occurred
 * @property description optional sentence on what the task is for
 * @property usage optional note on how the task is used
 * @property source class of the task action, as far as it can be told
 * @property interval repeat interval, or `null` for a one-shot task
 * @property lastDuration how long the latest finished invocation took
 * @property cancellable whether an administrator may stop the task from the commands and the menu
 * @property runnableNow whether an administrator may start the task at once from the commands and the menu
 * @property hidden whether the task asked to be left out of the commands and the menu
 * @property failedCount number of invocations that threw an error, including ones a task survived
 * @property parentId the task that started this one, or `null` for a task scheduled directly
 * @property lastLag how late the latest run started compared with its plan
 * @property worstLag the largest delay of any run so far, such as from a server freeze
 */
data class TaskSnapshot(
    val id: TaskId,
    val name: String?,
    val key: String?,
    val ownerName: String,
    val executionKind: TaskExecution.Kind,
    val status: TaskStatus,
    val tags: Set<String>,
    val createdAt: Instant,
    val nextRunAt: Instant?,
    val runCount: Long,
    val skippedCount: Long,
    val lastStartedAt: Instant?,
    val lastCompletedAt: Instant?,
    val lastFailure: String?,
    val description: String? = null,
    val usage: String? = null,
    val source: String? = null,
    val interval: Duration? = null,
    val lastDuration: Duration? = null,
    val cancellable: Boolean = true,
    val runnableNow: Boolean = true,
    val failedCount: Long = 0,
    val parentId: TaskId? = null,
    val lastLag: Duration? = null,
    val worstLag: Duration? = null,
    val hidden: Boolean = false,
) {
    /** Whether the task can still run, that is, it has not reached a terminal [status]. */
    val isActive: Boolean get() = !status.isTerminal
}
