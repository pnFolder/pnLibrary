package ru.privatenull.pnlibrary.core.config.actions

import ru.privatenull.pnlibrary.api.actions.Action
import ru.privatenull.pnlibrary.api.actions.impl.MessagesImpl
import ru.privatenull.pnlibrary.api.actions.impl.ActionBarImpl
import ru.privatenull.pnlibrary.api.config.ConfigSerializationContext
import ru.privatenull.pnlibrary.api.config.ConfigSerializer
import ru.privatenull.pnlibrary.api.text.ComponentSerializerType

/** Converts the readable `- message: ...` YAML form into a concrete action class. */
internal class ActionSerializer : ConfigSerializer<Action> {
    override fun serialize(value: Action, context: ConfigSerializationContext): Any = when (value) {
        is MessagesImpl -> mapOf("message" to messageBody(value))
        is ActionBarImpl -> mapOf("action-bar" to textBody(value.text, value.serializerType))
        else -> error("No configuration serializer is registered for action ${value.javaClass.name}")
    }

    override fun deserialize(value: Any?, context: ConfigSerializationContext): Action {
        require(value is Map<*, *> && value.size == 1) {
            "Action at ${context.path} must have one type, for example: { message: 'Hello' }"
        }
        val (rawName, body) = value.entries.single()
        return when (val name = rawName?.toString()?.trim()?.lowercase()) {
            "message", "messages" -> readMessage(body, context)
            "action-bar", "actionbar" -> readActionBar(body, context)
            else -> error("Unknown action '$name' at ${context.path}")
        }
    }

    private fun readActionBar(body: Any?, context: ConfigSerializationContext): ActionBarImpl {
        if (body is String) return ActionBarImpl(body)
        require(body is Map<*, *>) { "Action-bar action at ${context.path} must be text or an object" }
        val text = body.value("text")?.toString()
            ?: error("Action-bar action at ${context.path} requires 'text'")
        return ActionBarImpl(text, serializerType(body, context))
    }

    private fun readMessage(body: Any?, context: ConfigSerializationContext): MessagesImpl {
        if (body is String) return MessagesImpl(mutableListOf(body))
        if (body is List<*>) return MessagesImpl(body.map(Any?::toString).toMutableList())
        require(body is Map<*, *>) { "Message action at ${context.path} must be text, a list, or an object" }
        val messagesValue = body.value("messages") ?: body.value("message") ?: body.value("text") ?: emptyList<String>()
        val messages = when (messagesValue) {
            is List<*> -> messagesValue.map(Any?::toString).toMutableList()
            else -> mutableListOf(messagesValue.toString())
        }
        return MessagesImpl(messages, serializerType(body, context))
    }

    private fun serializerType(body: Map<*, *>, context: ConfigSerializationContext): ComponentSerializerType? {
        val serializer = body.value("serializer-type") ?: body.value("serializerType")
        return serializer?.toString()?.let { raw ->
            ComponentSerializerType.entries.firstOrNull { it.name.equals(raw, true) }
                ?: error("Unknown component serializer '$raw' at ${context.path}")
        }
    }

    private fun messageBody(action: MessagesImpl): Any {
        if (action.serializerType == null) return if (action.messages.size == 1) action.messages.single() else action.messages
        return linkedMapOf(
            "messages" to action.messages,
            "serializer-type" to action.serializerType!!.name,
        )
    }

    private fun textBody(text: String, serializerType: ComponentSerializerType?): Any =
        if (serializerType == null) text else linkedMapOf("text" to text, "serializer-type" to serializerType.name)

    private fun Map<*, *>.value(name: String): Any? = entries
        .firstOrNull { it.key?.toString()?.equals(name, true) == true }
        ?.value
}
