@file:Suppress("DEPRECATION")

package org.pnfolder.pnlibrary.api.actions.builtin

import org.pnfolder.pnlibrary.api.actions.Action
import org.pnfolder.pnlibrary.api.actions.ActionContext
import org.pnfolder.pnlibrary.api.config.ConfigKey
import org.pnfolder.pnlibrary.api.tasks.TaskExecution
import org.pnfolder.pnlibrary.api.tasks.TaskSpec
import java.time.Duration

/**
 * Runs a list of actions several times, [every] interval apart.
 *
 * The first run happens immediately; each later run is scheduled without blocking the calling thread.
 *
 * @property times how many runs, at least one
 * @property every pause between two runs
 * @property thenActions actions executed on every run
 * @throws IllegalArgumentException when [times] is below one or [every] is negative
 */
data class RepeatAction(
    val times: Int = 1,
    val every: Duration = Duration.ZERO,
    @field:ConfigKey("then")
    val thenActions: List<Action> = emptyList(),
) : Action {
    init {
        require(times in 1..MAX_TIMES) { "Repeat times must be from 1 to $MAX_TIMES" }
        require(!every.isNegative) { "Repeat interval must not be negative" }
    }

    override fun execute(context: ActionContext) {
        context.execute(thenActions)
        for (run in 1 until times) {
            context.tasks.schedule(TaskSpec.builder()
                .execution(TaskExecution.global())
                .delay(every.multipliedBy(run.toLong()))
                .action { context.execute(thenActions) }
                .build())
        }
    }

    companion object {
        /** The most runs one repeat may schedule. */
        const val MAX_TIMES = 1000
    }
}
