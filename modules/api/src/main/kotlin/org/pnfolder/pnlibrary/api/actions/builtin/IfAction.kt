package org.pnfolder.pnlibrary.api.actions.builtin

import org.pnfolder.pnlibrary.api.actions.Action
import org.pnfolder.pnlibrary.api.actions.ActionContext
import org.pnfolder.pnlibrary.api.checks.Check
import org.pnfolder.pnlibrary.api.config.ConfigKey

/**
 * Runs [thenActions] when [check] matches, otherwise [elseActions]. The rest of the list continues either way.
 *
 * ```yaml
 * - type: if
 *   check: vip            # a name from checks:, or an object with a type
 *   do:
 *     - type: message
 *       message: VIP bonus
 *   else:
 *     - type: message
 *       message: Buy VIP
 * ```
 *
 * A named check is only asked: its own `deny` and `success` do not run. Use [RequireAction] for that.
 * A missing [check] never matches.
 *
 * @property check the question
 * @property thenActions actions on "yes", written under `do`
 * @property elseActions actions on "no"
 */
data class IfAction(
    val check: Check? = null,
    @field:ConfigKey("then")
    val thenActions: List<Action> = emptyList(),
    @field:ConfigKey("else")
    val elseActions: List<Action> = emptyList(),
) : Action {
    override fun execute(context: ActionContext) {
        val matches = check?.matches(context) ?: false
        context.execute(if (matches) thenActions else elseActions)
    }
}
