package ru.privatenull.pnlibrary.core.config.actions

import ru.privatenull.pnlibrary.api.actions.Action
import ru.privatenull.pnlibrary.api.actions.MessagesAction
import ru.privatenull.pnlibrary.api.config.ConfigSerializationContext
import ru.privatenull.pnlibrary.api.config.ConfigSerializer
import ru.privatenull.pnlibrary.api.text.ComponentSerializerType

/** Converts the readable `- message: ...` YAML form into a concrete action class. */
internal class ActionSerializer : ConfigSerializer<Action> {
    override fun serialize(value: Action, context: ConfigSerializationContext): Any = when (value) {
        is MessagesAction -> mapOf("message" to messageBody(value))
        else -> error("No configuration serializer is registered for action ${value.javaClass.name}")
    }

    override fun deserialize(value: Any?, context: ConfigSerializationContext): Action {
        require(value is Map<*, *> && value.size == 1) {
            "Action at ${context.path} must have one type, for example: { message: 'Hello' }"
        }
        val (rawName, body) = value.entries.single()
        return when (val name = rawName?.toString()?.trim()?.lowercase()) {
            "message", "messages" -> readMessage(body, context)
            else -> error("Unknown action '$name' at ${context.path}")
        }
    }

    private fun readMessage(body: Any?, context: ConfigSerializationContext): MessagesAction {
        if (body is String) return MessagesAction(mutableListOf(body))
        if (body is List<*>) return MessagesAction(body.map(Any?::toString).toMutableList())
        require(body is Map<*, *>) { "Message action at ${context.path} must be text, a list, or an object" }
        val messagesValue = body.value("messages") ?: body.value("message") ?: body.value("text") ?: emptyList<String>()
        val messages = when (messagesValue) {
            is List<*> -> messagesValue.map(Any?::toString).toMutableList()
            else -> mutableListOf(messagesValue.toString())
        }
        val serializer = body.value("serializer-type") ?: body.value("serializerType")
        return MessagesAction(messages, serializer?.toString()?.let { raw ->
            ComponentSerializerType.entries.firstOrNull { it.name.equals(raw, true) }
                ?: error("Unknown component serializer '$raw' at ${context.path}")
        })
    }

    private fun messageBody(action: MessagesAction): Any {
        if (action.serializerType == null) return if (action.messages.size == 1) action.messages.single() else action.messages
        return linkedMapOf(
            "messages" to action.messages,
            "serializer-type" to action.serializerType!!.name,
        )
    }

    private fun Map<*, *>.value(name: String): Any? = entries
        .firstOrNull { it.key?.toString()?.equals(name, true) == true }
        ?.value
}
