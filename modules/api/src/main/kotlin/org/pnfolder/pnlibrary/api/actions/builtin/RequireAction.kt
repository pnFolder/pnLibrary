package org.pnfolder.pnlibrary.api.actions.builtin

import org.pnfolder.pnlibrary.api.actions.Action
import org.pnfolder.pnlibrary.api.actions.ActionContext
import org.pnfolder.pnlibrary.api.checks.Check
import org.pnfolder.pnlibrary.api.checks.Requirement
import org.pnfolder.pnlibrary.api.checks.builtin.NamedCheck
import org.pnfolder.pnlibrary.api.internal.Suggestions

/**
 * Stops the whole chain unless a check passes: the actions after it run only on "yes".
 *
 * ```yaml
 * - type: require
 *   check: has-100        # a name from checks: runs that check's own deny and success
 * - type: require
 *   check:                # or a check written in place, with its own deny
 *     type: permission
 *     permission: shop.use
 *   deny:
 *     - type: message
 *       message: No access
 * ```
 *
 * The DeluxeMenus block (`requirements:`, `minimum_requirements`, `deny_commands`, …) is read into [requirement].
 *
 * @property check a named or written-in-place check
 * @property requirement a full requirement written in place
 * @property deny actions run on "no" before the chain stops, after the named check's own `deny`
 */
data class RequireAction(
    val check: Check? = null,
    val requirement: Requirement? = null,
    val deny: List<Action> = emptyList(),
) : Action {
    override fun execute(context: ActionContext) {
        val passed = when {
            requirement != null -> requirement.test(context)
            check is NamedCheck -> named(context, check.name)
            check != null -> check.matches(context)
            else -> {
                context.logger.warning("A require action has no check and stops the chain")
                false
            }
        }
        if (!passed) {
            context.execute(deny)
            context.stop()
        }
    }

    private fun named(context: ActionContext, name: String): Boolean {
        val found = context.requirement(name)
        if (found == null) {
            context.logger.warning("Unknown check '$name'.${Suggestions.hint(name, context.requirementNames())}")
            return false
        }
        return found.test(context)
    }
}
