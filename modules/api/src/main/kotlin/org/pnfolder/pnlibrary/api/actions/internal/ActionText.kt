package org.pnfolder.pnlibrary.api.actions.internal

import org.pnfolder.pnlibrary.api.actions.Action
import org.pnfolder.pnlibrary.api.config.ConfigSerializationContext
import org.pnfolder.pnlibrary.api.config.ConfigTextParser

/**
 * Rejects an action written as one line of text and explains how to write it with a `type`.
 *
 * Actions are only written as objects. A line such as `[console] give %player_name% apple` is reported with
 * the object it stands for, so moving an old configuration is a matter of copying the suggestion.
 */
object ActionText : ConfigTextParser<Action> {
    private val tag = Regex("""^\[([A-Za-z]+)]\s*(.*)$""")

    override fun parse(text: String, context: ConfigSerializationContext): Action {
        val match = tag.find(text.trim())
        val example = match?.let { suggestion(it.groupValues[1].lowercase(), it.groupValues[2]) }
            ?: "- type: message\n  message: Hello"
        throw IllegalArgumentException("An action is written with a 'type', not as a line of text. Write it as:\n$example")
    }

    private fun suggestion(tag: String, argument: String): String? = when (tag) {
        "message", "broadcast", "minimessage", "minibroadcast", "json", "jsonbroadcast" ->
            "- type: message\n  message: $argument"
        "console" -> "- type: console\n  command: $argument"
        "player", "commandevent" -> "- type: player\n  command: $argument"
        "actionbar" -> "- type: actionbar\n  text: $argument"
        "sound", "broadcastsound" -> "- type: sound\n  sound: ${argument.substringBefore(' ')}"
        "title", "broadcasttitle" -> "- type: title\n  title: ${argument.substringBefore(';')}"
        "log" -> "- type: log\n  text: $argument"
        "run" -> "- type: run\n  name: $argument"
        "stop" -> "- type: stop"
        else -> null
    }
}
