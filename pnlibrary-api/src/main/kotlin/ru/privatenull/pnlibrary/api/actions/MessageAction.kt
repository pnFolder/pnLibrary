package ru.privatenull.pnlibrary.api.actions

import ru.privatenull.pnlibrary.api.text.ComponentSerializerType
import ru.privatenull.pnlibrary.api.config.ConfigType

/** Sends one multiline message to the selected audience. */
@ConfigType("message")
data class MessageAction(
    val messages: List<String> = emptyList(),
    /** `null` inherits the serializer selected by the execution context. */
    val serializerType: ComponentSerializerType? = null,
    val target: Action.Target = Action.Target.PLAYER,
) : Action {
    override fun execute(context: Action.Context) {
        context.target(target).sendMessage(context.component(messages, serializerType))
    }
}
