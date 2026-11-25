package org.pnfolder.pnlibrary.api.scenario.actions.builtin

import org.pnfolder.pnlibrary.api.scenario.actions.Action
import org.pnfolder.pnlibrary.api.scenario.actions.ActionContext
import org.pnfolder.pnlibrary.api.scenario.actions.ActionResult
import org.pnfolder.pnlibrary.api.scenario.actions.ActionTarget
import org.pnfolder.pnlibrary.api.text.serializer.ComponentSerializerType

/**
 * Shows text above the hotbar.
 *
 * @property text the line; placeholders are filled in
 * @property serializerType text format override, or `null` for the default
 * @property target who sees it
 */
data class ActionBarAction(
    val text: String = "",
    val serializerType: ComponentSerializerType? = null,
    val target: ActionTarget = ActionTarget.PLAYER,
) : Action {
    override fun run(context: ActionContext): ActionResult {
        context.target(target).sendActionBar(context.component(text, serializerType))
        return ActionResult.Done
    }
}
