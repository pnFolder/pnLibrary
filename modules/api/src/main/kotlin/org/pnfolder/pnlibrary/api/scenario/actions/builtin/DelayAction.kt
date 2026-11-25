package org.pnfolder.pnlibrary.api.scenario.actions.builtin

import org.pnfolder.pnlibrary.api.config.ConfigKey
import org.pnfolder.pnlibrary.api.scenario.actions.Action
import org.pnfolder.pnlibrary.api.scenario.actions.ActionContext
import org.pnfolder.pnlibrary.api.scenario.actions.ActionResult
import java.time.Duration

/**
 * Runs [steps] after [after] without waiting; the scenario goes on at once.
 *
 * The delayed part is its own scenario: a failure there is logged and undoes only its own steps.
 *
 * @property after the pause
 * @property steps what runs afterwards
 */
data class DelayAction(
    val after: Duration = Duration.ofSeconds(1),
    @field:ConfigKey("then")
    val steps: List<Action> = emptyList(),
) : Action {
    init { require(!after.isNegative) { "Delay must not be negative" } }

    override fun run(context: ActionContext): ActionResult {
        context.runLater(after, steps)
        return ActionResult.Done
    }
}
