package ru.privatenull.pnlibrary.core.config.actions

import ru.privatenull.pnlibrary.api.actions.Action
import ru.privatenull.pnlibrary.api.actions.MessageAction
import ru.privatenull.pnlibrary.api.actions.ActionBarAction
import ru.privatenull.pnlibrary.api.actions.SoundAction
import ru.privatenull.pnlibrary.api.actions.ConsoleLogAction
import ru.privatenull.pnlibrary.api.actions.DelayAction
import ru.privatenull.pnlibrary.api.logging.LogLevel
import net.kyori.adventure.sound.Sound
import java.time.Duration
import ru.privatenull.pnlibrary.api.config.ConfigSerializationContext
import ru.privatenull.pnlibrary.api.config.ConfigSerializer
import ru.privatenull.pnlibrary.api.text.ComponentSerializerType

/** Converts the readable `- message: ...` YAML form into a concrete action class. */
internal class ActionSerializer : ConfigSerializer<Action> {
    override fun serialize(value: Action, context: ConfigSerializationContext): Any = when (value) {
        is MessageAction -> mapOf("message" to messageBody(value))
        is ActionBarAction -> mapOf("action-bar" to textBody(value.text, value.serializerType, value.target))
        is SoundAction -> mapOf("sound" to linkedMapOf(
            "key" to value.key, "source" to value.source.name, "volume" to value.volume,
            "pitch" to value.pitch, "target" to value.target.name,
        ))
        is ConsoleLogAction -> mapOf("console" to linkedMapOf("text" to value.text, "level" to value.level.name))
        is DelayAction -> mapOf("delay" to linkedMapOf(
            "duration" to formatDuration(value.duration),
            "actions" to value.actions.map { serialize(it, context) },
        ))
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
            "broadcast-action-bar", "broadcast-actionbar" -> readActionBar(body, context, Action.Target.ALL)
            "sound" -> readSound(body, context)
            "console", "log" -> readConsole(body, context)
            "delay", "later" -> readDelay(body, context)
            else -> error("Unknown action '$name' at ${context.path}")
        }
    }

    private fun readSound(body: Any?, context: ConfigSerializationContext): SoundAction {
        if (body is String) return SoundAction(key = body)
        require(body is Map<*, *>) { "Sound action at ${context.path} must be a key or an object" }
        return SoundAction(
            key = body.value("key")?.toString() ?: error("Sound action at ${context.path} requires 'key'"),
            source = enumValue(body.value("source"), Sound.Source.MASTER, context),
            volume = body.value("volume")?.toString()?.toFloatOrNull() ?: 1f,
            pitch = body.value("pitch")?.toString()?.toFloatOrNull() ?: 1f,
            target = enumValue(body.value("target"), Action.Target.PLAYER, context),
        )
    }

    private fun readConsole(body: Any?, context: ConfigSerializationContext): ConsoleLogAction {
        if (body is String) return ConsoleLogAction(body)
        require(body is Map<*, *>) { "Console action at ${context.path} must be text or an object" }
        return ConsoleLogAction(
            text = body.value("text")?.toString() ?: error("Console action at ${context.path} requires 'text'"),
            level = enumValue(body.value("level"), LogLevel.INFO, context),
        )
    }

    private fun readDelay(body: Any?, context: ConfigSerializationContext): DelayAction {
        require(body is Map<*, *>) { "Delay action at ${context.path} must be an object" }
        val rawActions = body.value("actions")
        require(rawActions is List<*>) { "Delay action at ${context.path} requires an 'actions' list" }
        return DelayAction(
            duration = parseDuration(body.value("duration")?.toString() ?: "0s", context.path),
            actions = rawActions.mapIndexed { index, action ->
                deserialize(action, context.copy(path = "${context.path}.actions[$index]"))
            },
        )
    }

    private fun readActionBar(
        body: Any?,
        context: ConfigSerializationContext,
        defaultTarget: Action.Target = Action.Target.PLAYER,
    ): ActionBarAction {
        if (body is String) return ActionBarAction(body, target = defaultTarget)
        require(body is Map<*, *>) { "Action-bar action at ${context.path} must be text or an object" }
        val text = body.value("text")?.toString()
            ?: error("Action-bar action at ${context.path} requires 'text'")
        val target = body.value("target")?.toString()?.let { raw ->
            Action.Target.entries.firstOrNull { it.name.equals(raw, true) }
                ?: error("Unknown action target '$raw' at ${context.path}; allowed: PLAYER, ALL")
        } ?: defaultTarget
        return ActionBarAction(text, serializerType(body, context), target)
    }

    private fun readMessage(body: Any?, context: ConfigSerializationContext): MessageAction {
        if (body is String) return MessageAction(listOf(body))
        if (body is List<*>) return MessageAction(body.map(Any?::toString))
        require(body is Map<*, *>) { "Message action at ${context.path} must be text, a list, or an object" }
        val messagesValue = body.value("messages") ?: body.value("message") ?: body.value("text") ?: emptyList<String>()
        val messages = when (messagesValue) {
            is List<*> -> messagesValue.map(Any?::toString)
            else -> listOf(messagesValue.toString())
        }
        return MessageAction(
            messages,
            serializerType(body, context),
            enumValue(body.value("target"), Action.Target.PLAYER, context),
        )
    }

    private fun serializerType(body: Map<*, *>, context: ConfigSerializationContext): ComponentSerializerType? {
        val serializer = body.value("serializer-type") ?: body.value("serializerType")
        return serializer?.toString()?.let { raw ->
            ComponentSerializerType.entries.firstOrNull { it.name.equals(raw, true) }
                ?: error("Unknown component serializer '$raw' at ${context.path}")
        }
    }

    private fun messageBody(action: MessageAction): Any {
        if (action.serializerType == null && action.target == Action.Target.PLAYER)
            return if (action.messages.size == 1) action.messages.single() else action.messages
        return linkedMapOf<String, Any>("messages" to action.messages).apply {
            action.serializerType?.let { put("serializer-type", it.name) }
            if (action.target != Action.Target.PLAYER) put("target", action.target.name)
        }
    }

    private fun textBody(
        text: String,
        serializerType: ComponentSerializerType?,
        target: Action.Target,
    ): Any = if (serializerType == null && target == Action.Target.PLAYER) text else linkedMapOf<String, Any>("text" to text).apply {
        serializerType?.let { put("serializer-type", it.name) }
        if (target != Action.Target.PLAYER) put("target", target.name)
    }

    private fun Map<*, *>.value(name: String): Any? = entries
        .firstOrNull { it.key?.toString()?.equals(name, true) == true }
        ?.value

    private inline fun <reified E : Enum<E>> enumValue(value: Any?, default: E, context: ConfigSerializationContext): E {
        if (value == null) return default
        return enumValues<E>().firstOrNull { it.name.equals(value.toString(), true) }
            ?: error("Unknown ${E::class.java.simpleName} '${value}' at ${context.path}")
    }

    private fun parseDuration(value: String, path: String): Duration {
        val match = Regex("^([0-9]+)(ms|s|m|h)$", RegexOption.IGNORE_CASE).matchEntire(value.trim())
            ?: return runCatching { Duration.parse(value.uppercase()) }
                .getOrElse { error("Invalid duration '$value' at $path") }
        val amount = match.groupValues[1].toLong()
        return when (match.groupValues[2].lowercase()) {
            "ms" -> Duration.ofMillis(amount)
            "s" -> Duration.ofSeconds(amount)
            "m" -> Duration.ofMinutes(amount)
            else -> Duration.ofHours(amount)
        }
    }

    private fun formatDuration(value: Duration): String = when {
        value.toMillis() % 3_600_000 == 0L -> "${value.toHours()}h"
        value.toMillis() % 60_000 == 0L -> "${value.toMinutes()}m"
        value.toMillis() % 1_000 == 0L -> "${value.seconds}s"
        else -> "${value.toMillis()}ms"
    }
}
