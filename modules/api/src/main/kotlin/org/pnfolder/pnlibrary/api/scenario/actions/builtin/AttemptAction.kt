package org.pnfolder.pnlibrary.api.scenario.actions.builtin

import org.pnfolder.pnlibrary.api.config.ConfigKey
import org.pnfolder.pnlibrary.api.scenario.actions.Action
import org.pnfolder.pnlibrary.api.scenario.actions.ActionContext
import org.pnfolder.pnlibrary.api.scenario.actions.ActionResult

/**
 * Runs [steps] as a unit that may fail. When a step fails, everything that unit did is undone (a charge is given
 * back), then the first of [handlers] that takes the failure's code runs, or [fail] when none does.
 * The scenario then ends, or goes on when [resume] is set.
 *
 * Inside a handler the cause is available as `[error]`, its code as `[error_code]`.
 *
 * ```yaml
 * - type: attempt
 *   do:
 *     - { type: charge, currency: money, amount: 100 }
 *     - { type: command, command: 'give [player] diamond 1' }
 *   on:
 *     - codes: [currency.insufficient-funds]
 *       do: [ { type: message, messages: ['&cYou need 100 coins'] } ]
 *     - codes: ['currency.*']
 *       do: [ { type: message, messages: ['&cThe bank is not answering, try later'] } ]
 *   fail:
 *     - { type: message, messages: ['&cCould not buy: [error]'] }
 * ```
 *
 * @property steps the actions that may fail
 * @property handlers reactions by failure code, the first match wins
 * @property fail actions run for a failure no handler takes
 * @property resume whether the rest of the list runs after a handled failure
 * @property undo whether the steps are undone on a failure; with `false` what they did stays, and an outer
 * failure does not undo it either
 */
data class AttemptAction(
    @field:ConfigKey("do")
    val steps: List<Action> = emptyList(),
    @field:ConfigKey("on")
    val handlers: List<ErrorHandler> = emptyList(),
    val fail: List<Action> = emptyList(),
    val resume: Boolean = false,
    val undo: Boolean = true,
) : Action {
    override fun run(context: ActionContext): ActionResult {
        val mark = context.undoMark()
        val result = context.runList(steps)
        if (result !is ActionResult.Failed) return result
        if (undo) context.rollbackTo(mark) else context.discardUndo(mark)
        context.setValue("error", result.message).setValue("error_code", result.code)
        val reaction = handlers.firstOrNull { it.matches(result.code) }?.steps ?: fail
        val handled = context.runList(reaction)
        return when {
            handled is ActionResult.Failed -> handled
            resume -> ActionResult.Done
            else -> ActionResult.Stopped(result.message)
        }
    }
}
