package org.pnfolder.pnlibrary.api.tasks.info

import java.time.Duration
import java.time.Instant

/**
 * How late one run of a task started, such as after the server froze.
 *
 * @property taskId the task that is late
 * @property taskName its name, when it has one
 * @property runNumber the one-based number of the run that is about to start
 * @property planned when the run should have started
 * @property started when it actually starts
 * @property interval the repeat interval of the task, or `null` for a one-shot task
 */
data class TaskLag(
    val taskId: TaskId,
    val taskName: String?,
    val runNumber: Long,
    val planned: Instant,
    val started: Instant,
    val interval: Duration?,
) {
    /** How late the run is; never negative. */
    val lag: Duration get() = Duration.between(planned, started).takeUnless(Duration::isNegative) ?: Duration.ZERO

    /** How many whole runs of a repeating task fitted into the delay and were lost; `0` for a one-shot task. */
    val missedRuns: Long
        get() = interval?.takeUnless { it.isZero }?.let { lag.toMillis() / it.toMillis() } ?: 0L

    /** Whether the run is late by more than [threshold]. */
    fun isOver(threshold: Duration): Boolean = lag > threshold

    /** A short description for logs and messages, such as `Autosave started 20s late (about 20 runs missed)`. */
    override fun toString(): String = buildString {
        append(taskName ?: taskId.toString()).append(" started ").append(lag.seconds).append("s late")
        if (missedRuns > 0) append(" (about ").append(missedRuns).append(" runs missed)")
    }
}
