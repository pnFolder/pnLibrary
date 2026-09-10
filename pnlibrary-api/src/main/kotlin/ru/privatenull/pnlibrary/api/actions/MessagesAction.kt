package ru.privatenull.pnlibrary.api.actions

import ru.privatenull.pnlibrary.api.text.ComponentSerializerType

class MessagesAction(
    var messages: MutableList<String> = mutableListOf(),
    /** `null` means: inherit the serializer selected by the execution context. */
    var serializerType: ComponentSerializerType? = null,
) : Action {

    override fun execute(context: Action.Context) {
        messages.forEach { source ->
            context.player.sendMessage(context.component(source, serializerType))
        }
    }
}
