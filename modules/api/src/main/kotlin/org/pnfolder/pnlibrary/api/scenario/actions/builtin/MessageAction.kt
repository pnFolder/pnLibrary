package org.pnfolder.pnlibrary.api.scenario.actions.builtin

import org.pnfolder.pnlibrary.api.scenario.actions.Action
import org.pnfolder.pnlibrary.api.scenario.actions.ActionContext
import org.pnfolder.pnlibrary.api.scenario.actions.ActionResult
import org.pnfolder.pnlibrary.api.scenario.actions.ActionTarget
import org.pnfolder.pnlibrary.api.text.serializer.ComponentSerializerType

/**
 * Sends chat text; the lines become one message, one under another.
 *
 * @property messages text lines; placeholders are filled in
 * @property serializerType text format override, or `null` for the default
 * @property target who receives it
 */
data class MessageAction(
    val messages: List<String> = emptyList(),
    val serializerType: ComponentSerializerType? = null,
    val target: ActionTarget = ActionTarget.PLAYER,
) : Action {
    override fun run(context: ActionContext): ActionResult {
        context.target(target).sendMessage(context.component(messages, serializerType))
        return ActionResult.Done
    }
}
