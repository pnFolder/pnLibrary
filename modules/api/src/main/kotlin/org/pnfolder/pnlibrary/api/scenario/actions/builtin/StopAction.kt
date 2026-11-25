package org.pnfolder.pnlibrary.api.scenario.actions.builtin

import org.pnfolder.pnlibrary.api.scenario.actions.Action
import org.pnfolder.pnlibrary.api.scenario.actions.ActionContext
import org.pnfolder.pnlibrary.api.scenario.actions.ActionResult

/**
 * Ends the scenario here, quietly and without undoing anything.
 *
 * @property reason optional note for the result
 */
data class StopAction(val reason: String? = null) : Action {
    override fun run(context: ActionContext): ActionResult = ActionResult.Stopped(reason)
}
