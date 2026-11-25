package org.pnfolder.pnlibrary.api.scenario.actions.builtin

import org.pnfolder.pnlibrary.api.config.ConfigKey
import org.pnfolder.pnlibrary.api.scenario.actions.Action
import org.pnfolder.pnlibrary.api.scenario.actions.ActionContext
import org.pnfolder.pnlibrary.api.scenario.actions.ActionResult
import java.time.Duration

/**
 * Runs [steps] [times] times, [every] apart. The first run is immediate and part of the scenario; later runs
 * are separate scenarios.
 *
 * @property times how many runs, from 1 to [MAX_TIMES]
 * @property every pause between runs
 * @property steps what runs each time
 */
data class RepeatAction(
    val times: Int = 1,
    val every: Duration = Duration.ZERO,
    @field:ConfigKey("then")
    val steps: List<Action> = emptyList(),
) : Action {
    init {
        require(times in 1..MAX_TIMES) { "Repeat times must be from 1 to $MAX_TIMES" }
        require(!every.isNegative) { "Repeat interval must not be negative" }
    }

    override fun run(context: ActionContext): ActionResult {
        for (run in 1 until times) context.runLater(every.multipliedBy(run.toLong()), steps)
        return context.runList(steps)
    }

    /** Limits for repeat actions. */
    companion object {
        /** The most runs one repeat may schedule. */
        const val MAX_TIMES = 1000
    }
}
