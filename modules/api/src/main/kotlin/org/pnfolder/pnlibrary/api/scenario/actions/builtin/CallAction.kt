package org.pnfolder.pnlibrary.api.scenario.actions.builtin

import org.pnfolder.pnlibrary.api.scenario.actions.Action
import org.pnfolder.pnlibrary.api.scenario.actions.ActionContext
import org.pnfolder.pnlibrary.api.scenario.actions.ActionResult

/**
 * Runs an action a plugin defined by name (see `ActionService.define`), giving it [args] as values for the call.
 * The arguments are visible inside the call only. Calls may nest to [MAX_DEPTH] levels.
 *
 * ```yaml
 * - { type: call, name: give-kit, args: { kit: starter, amount: '{= 2 * level()}' } }
 * ```
 *
 * @property name the defined name
 * @property args values for the call; placeholders in them are filled in first
 */
data class CallAction(
    val name: String = "",
    val args: Map<String, String> = emptyMap(),
) : Action {
    override fun run(context: ActionContext): ActionResult {
        val target = context.action(name) ?: return ActionResult.Failed("call.unknown", "Action '$name' is not defined")
        val depth = (context.value(DEPTH) as? Number)?.toInt() ?: 0
        if (depth >= MAX_DEPTH) return ActionResult.Failed("call.too-deep", "Action '$name' is called too deeply; check for a loop")
        val values = args.mapValues { context.format(it.value) } + (DEPTH to depth + 1)
        return context.scoped(values) { target.run(context) }
    }

    /** Limits for calls. */
    companion object {
        /** The deepest chain of calls inside calls. */
        const val MAX_DEPTH = 16
        private const val DEPTH = "_call_depth"
    }
}
