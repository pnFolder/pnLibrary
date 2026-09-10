package ru.privatenull.pnlibrary.core.config.actions

import java.time.Duration
import ru.privatenull.pnlibrary.api.actions.PlayerAction
import ru.privatenull.pnlibrary.api.config.ConfigSerializationContext
import ru.privatenull.pnlibrary.api.config.ConfigSerializer

/** Human-friendly, recursive YAML representation of one configured player action. */
internal class PlayerActionSerializer : ConfigSerializer<PlayerAction> {
    override fun serialize(value: PlayerAction, context: ConfigSerializationContext): Any {
        val body = linkedMapOf<String, Any?>()
        when (value.type.lowercase()) {
            "message", "action_bar", "kick" -> if (simpleText(value)) return mapOf(value.type to value.text.orEmpty())
            "sound" -> if (value.volume == 1f && value.soundPitch == 1f && value.sound != null)
                return mapOf(value.type to value.sound!!)
        }
        value.text?.let { body["text"] = it }
        value.title?.let { body["title"] = it }
        value.subtitle?.let { body["subtitle"] = it }
        if (value.fadeIn != 10) body["fade-in"] = value.fadeIn
        if (value.stay != 70) body["stay"] = value.stay
        if (value.fadeOut != 20) body["fade-out"] = value.fadeOut
        value.world?.let { body["world"] = it }
        if (value.x != 0.0) body["x"] = value.x
        if (value.y != 0.0) body["y"] = value.y
        if (value.z != 0.0) body["z"] = value.z
        if (value.yaw != 0f) body["yaw"] = value.yaw
        if (value.pitch != 0f) body["pitch"] = value.pitch
        value.sound?.let { body["key"] = it }
        if (value.volume != 1f) body["volume"] = value.volume
        if (value.soundPitch != 1f) body["pitch"] = value.soundPitch
        value.command?.let { body["command"] = it }
        if (!value.duration.isZero) body["duration"] = formatDuration(value.duration)
        if (value.actions.isNotEmpty()) body["actions"] = value.actions.map { serialize(it, context) }
        body.putAll(value.arguments)
        return mapOf(value.type to body)
    }

    override fun deserialize(value: Any?, context: ConfigSerializationContext): PlayerAction {
        require(value is Map<*, *>) { "Action at ${context.path} must be a YAML object" }
        val typeEntry = value.entries.firstOrNull { it.key?.toString()?.equals("type", true) == true }
        val type: String
        val body: Any?
        if (typeEntry != null) {
            type = typeEntry.value?.toString()?.trim().orEmpty()
            body = linkedMapOf<Any?, Any?>().also { out ->
                value.forEach { (key, item) -> if (key != typeEntry.key) out[key] = item }
            }
        } else {
            require(value.size == 1) {
                "Action at ${context.path} must contain exactly one handler, for example: { message: '<green>Hello' }"
            }
            val entry = value.entries.single()
            type = entry.key?.toString()?.trim().orEmpty()
            body = entry.value
        }
        require(type.isNotEmpty()) { "Action handler at ${context.path} must not be blank" }
        val action = PlayerAction(type)
        if (body !is Map<*, *>) {
            when (type.lowercase()) {
                "message", "action_bar", "kick" -> action.text = body?.toString().orEmpty()
                "sound" -> action.sound = body?.toString().orEmpty()
                "player_command" -> action.command = body?.toString().orEmpty()
                else -> action.arguments["value"] = body
            }
            return action
        }
        body.forEach { (rawKey, rawValue) ->
            val key = rawKey?.toString()?.trim()?.lowercase()?.replace('_', '-') ?: return@forEach
            when (key) {
                "text", "message" -> action.text = rawValue?.toString()
                "title" -> action.title = rawValue?.toString()
                "subtitle" -> action.subtitle = rawValue?.toString()
                "fade-in" -> action.fadeIn = number(rawValue, context, key).toInt()
                "stay" -> action.stay = number(rawValue, context, key).toInt()
                "fade-out" -> action.fadeOut = number(rawValue, context, key).toInt()
                "world" -> action.world = rawValue?.toString()
                "x" -> action.x = number(rawValue, context, key).toDouble()
                "y" -> action.y = number(rawValue, context, key).toDouble()
                "z" -> action.z = number(rawValue, context, key).toDouble()
                "yaw" -> action.yaw = number(rawValue, context, key).toFloat()
                "pitch" -> if (type.equals("sound", true)) action.soundPitch = number(rawValue, context, key).toFloat()
                           else action.pitch = number(rawValue, context, key).toFloat()
                "key", "sound" -> action.sound = rawValue?.toString()
                "volume" -> action.volume = number(rawValue, context, key).toFloat()
                "command" -> action.command = rawValue?.toString()
                "duration", "delay" -> action.duration = parseDuration(rawValue?.toString().orEmpty(), context.path)
                "actions" -> {
                    require(rawValue is List<*>) { "${context.path}.actions must be a YAML list" }
                    action.actions = rawValue.mapIndexed { index, child ->
                        deserialize(child, context.copy(path = "${context.path}.actions[$index]"))
                    }.toMutableList()
                }
                "arguments" -> {
                    require(rawValue is Map<*, *>) { "${context.path}.arguments must be a YAML object" }
                    rawValue.forEach { (argument, item) -> action.arguments[argument.toString()] = item }
                }
                else -> action.arguments[rawKey.toString()] = rawValue
            }
        }
        return action
    }

    private fun simpleText(value: PlayerAction) = value.text != null && value.arguments.isEmpty() && value.actions.isEmpty()

    private fun number(value: Any?, context: ConfigSerializationContext, key: String): Number =
        value as? Number ?: value?.toString()?.toDoubleOrNull()
        ?: error("${context.path}.$key must be a number")

    private fun parseDuration(source: String, path: String): Duration {
        val match = Regex("^([0-9]+(?:\\.[0-9]+)?)(ms|s|m|h|d)$", RegexOption.IGNORE_CASE).matchEntire(source.trim())
            ?: runCatching { return Duration.parse(source.trim().uppercase()) }.getOrElse {
                error("Invalid duration '$source' at $path; use 500ms, 2s, 5m, 1h or ISO-8601")
            }
        val amount = match.groupValues[1].toDouble()
        val millis = when (match.groupValues[2].lowercase()) {
            "ms" -> amount
            "s" -> amount * 1_000
            "m" -> amount * 60_000
            "h" -> amount * 3_600_000
            else -> amount * 86_400_000
        }
        return Duration.ofMillis(millis.toLong())
    }

    private fun formatDuration(value: Duration): String = when {
        value.toMillis() % 3_600_000L == 0L -> "${value.toHours()}h"
        value.toMillis() % 60_000L == 0L -> "${value.toMinutes()}m"
        value.toMillis() % 1_000L == 0L -> "${value.seconds}s"
        else -> "${value.toMillis()}ms"
    }
}
