package org.pnfolder.pnlibrary.api.tasks.run

import org.pnfolder.pnlibrary.api.tasks.info.TaskLag

/**
 * Reacts when a run starts later than the task's lag threshold, for example because the server froze.
 *
 * Use it to tell an administrator, to log, or to decide that a stale run is not worth doing:
 *
 * ```kotlin
 * .onLag(Duration.ofSeconds(5)) { lag ->
 *     log("$lag")
 *     if (lag.missedRuns > 10) LagResponse.SKIP else LagResponse.RUN
 * }
 * ```
 *
 * It runs on the task's own thread right before the run. An exception it throws is logged and the run goes on.
 */
fun interface TaskLagHandler {
    /** Decides what happens to the late run described by [lag]. */
    fun handle(lag: TaskLag): LagResponse
}
