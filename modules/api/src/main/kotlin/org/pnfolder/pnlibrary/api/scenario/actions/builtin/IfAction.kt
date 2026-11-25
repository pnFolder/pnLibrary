package org.pnfolder.pnlibrary.api.scenario.actions.builtin

import org.pnfolder.pnlibrary.api.config.ConfigKey
import org.pnfolder.pnlibrary.api.scenario.actions.Action
import org.pnfolder.pnlibrary.api.scenario.actions.ActionContext
import org.pnfolder.pnlibrary.api.scenario.actions.ActionResult
import org.pnfolder.pnlibrary.api.scenario.requirements.Requirement

/**
 * Runs [thenActions] when [requirement] is met, otherwise [elseActions]; the list goes on afterwards.
 * A failure inside the chosen branch fails the whole scenario.
 *
 * ```yaml
 * - type: if
 *   when: { type: expression, expression: "level() >= 10" }
 *   then: [ { type: message, messages: ['Veteran price'] } ]
 *   else: [ { type: message, messages: ['Normal price'] } ]
 * ```
 *
 * @property requirement the question; a missing one is never met
 */
data class IfAction(
    @field:ConfigKey("when")
    val requirement: Requirement? = null,
    @field:ConfigKey("then")
    val thenActions: List<Action> = emptyList(),
    @field:ConfigKey("else")
    val elseActions: List<Action> = emptyList(),
) : Action {
    override fun run(context: ActionContext): ActionResult {
        val met = requirement?.test(context)?.isMet ?: false
        return context.runList(if (met) thenActions else elseActions)
    }
}
