package org.pnfolder.pnlibrary.api.actions.builtin

import org.pnfolder.pnlibrary.api.actions.Action
import org.pnfolder.pnlibrary.api.actions.ActionContext
import org.pnfolder.pnlibrary.api.actions.ActionTarget
import org.pnfolder.pnlibrary.api.text.ComponentSerializerType

/**
 * Parses and displays one action-bar component to the selected audience.
 *
 * @property text serialized component text; an empty string clears or replaces the
 * current action bar according to platform behavior
 * @property serializerType parser override, or `null` to inherit [ActionContext.serializerType]
 * @property target audience that receives the component
 */
data class ActionBarAction(
    val text: String = "",
    val serializerType: ComponentSerializerType? = null,
    val target: ActionTarget = ActionTarget.PLAYER,
) : Action {
    override fun execute(context: ActionContext) {
        context.target(target).actionBar(context.component(text, serializerType))
    }
}
