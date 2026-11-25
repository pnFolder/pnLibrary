package org.pnfolder.pnlibrary.api.scenario.actions.builtin

import org.pnfolder.pnlibrary.api.config.ConfigKey
import org.pnfolder.pnlibrary.api.scenario.actions.Action
import org.pnfolder.pnlibrary.api.scenario.actions.ActionContext
import org.pnfolder.pnlibrary.api.scenario.actions.ActionResult
import org.pnfolder.pnlibrary.api.scenario.requirements.Requirement
import org.pnfolder.pnlibrary.api.scenario.requirements.Verdict

/**
 * Ends the scenario quietly when [requirement] is not met, after running [deny].
 *
 * Inside [deny] the reason is available as `[reason]`.
 *
 * ```yaml
 * - type: require
 *   when: { type: permission, permission: shop.use }
 *   deny: [ { type: message, messages: ['&c[reason]'] } ]
 * ```
 *
 * @property requirement what must hold; a missing one is never met
 * @property deny actions run when it does not
 */
data class RequireAction(
    @field:ConfigKey("when")
    val requirement: Requirement? = null,
    val deny: List<Action> = emptyList(),
) : Action {
    override fun run(context: ActionContext): ActionResult {
        val verdict = requirement?.test(context) ?: Verdict.Unmet("no requirement is configured")
        if (verdict !is Verdict.Unmet) return ActionResult.Done
        context.setValue("reason", verdict.reason)
        val denied = context.runList(deny)
        return if (denied is ActionResult.Failed) denied else ActionResult.Stopped(verdict.reason)
    }
}
