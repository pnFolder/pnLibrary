package ru.privatenull.pnlibrary.api.actions

import ru.privatenull.pnlibrary.api.text.ComponentSerializerType

/**
 * Parses a sequence of text lines and sends it as one component to an audience.
 *
 * Line joining and empty-list behavior are defined by [ActionContext.components], so
 * every action uses the same text rules as the rest of the owning plugin.
 *
 * @property messages serialized component lines in display order
 * @property serializerType parser override, or `null` to inherit [ActionContext.serializerType]
 * @property target audience that receives the resulting component
 */
data class MessageAction(
    val messages: List<String> = emptyList(),
    val serializerType: ComponentSerializerType? = null,
    val target: ActionTarget = ActionTarget.PLAYER,
) : Action {
    override fun execute(context: ActionContext) {
        context.target(target).sendMessage(context.component(messages, serializerType))
    }
}
