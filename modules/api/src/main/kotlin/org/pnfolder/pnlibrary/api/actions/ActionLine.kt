package org.pnfolder.pnlibrary.api.actions

import org.pnfolder.pnlibrary.api.config.ConfigSerializationContext
import org.pnfolder.pnlibrary.api.config.ConfigTextParser
import org.pnfolder.pnlibrary.api.logging.LogLevel
import org.pnfolder.pnlibrary.api.text.ComponentSerializerType
import java.time.Duration
import java.util.Locale

/**
 * Reads and writes an action as one line: `[tag] arguments<delay=20><chance=50>`.
 *
 * The tag selects the action. `<delay=…>` waits that many ticks (twenty per second, or a number
 * with `s`, such as `2s`) before the action runs. `<chance=…>` runs it with that percent chance,
 * from 0 to 100. Both tags go at the end of the line, in any order.
 *
 * | Tag | Arguments |
 * |---|---|
 * | `[message]`, `[broadcast]` | text for the player, text for every player |
 * | `[minimessage]`, `[minibroadcast]` | the same, read as MiniMessage |
 * | `[json]`, `[jsonbroadcast]` | the same, read as JSON |
 * | `[actionbar]` | text above the hotbar |
 * | `[player]`, `[commandevent]`, `[console]` | a command without the slash |
 * | `[sound]`, `[broadcastsound]` | `name volume pitch` |
 * | `[log]` | text for the server log |
 * | `[stop]` | none; ends the whole chain |
 * | `[wait 2s]` | pauses the rest of the list; time in ticks (`[wait 40]`) or seconds (`[wait 2s]`) |
 * | `[title]`, `[broadcasttitle]` | `title;subtitle;fadeIn;stay;fadeOut`, times in ticks, all but the title optional |
 * | `[run]` | the name of a template defined by the plugin |
 *
 * `[[player]]`, `%player_name%` and any other placeholder are replaced when the action runs.
 */
object ActionLine : ConfigTextParser<Action> {
    private val modifier = Regex("""\s*<(delay|chance)=([^<>]*)>\s*$""", RegexOption.IGNORE_CASE)
    private const val TICK_MILLIS = 50L
    private val WAIT_HEAD = Regex("""^wait\s+(.+)$""")

    override fun parse(text: String, context: ConfigSerializationContext): Action = parse(text)

    /**
     * Reads [line] as an action.
     *
     * @throws IllegalArgumentException when the tag is unknown or an argument is invalid
     */
    fun parse(line: String): Action {
        var rest = line.trim()
        var delay: Duration? = null
        var chance: Double? = null
        while (true) {
            val found = modifier.find(rest) ?: break
            val value = found.groupValues[2].trim()
            when (found.groupValues[1].lowercase(Locale.ROOT)) {
                "delay" -> delay = delay ?: parseDelay(value)
                else -> chance = chance ?: parseChance(value)
            }
            rest = rest.substring(0, found.range.first)
        }
        require(rest.startsWith("[") && rest.contains(']')) {
            "An action must start with a [tag], for example \"[message] Hello\", but was '$line'"
        }
        val head = rest.substring(1, rest.indexOf(']')).trim().lowercase(Locale.ROOT)
        val inlineWait = WAIT_HEAD.matchEntire(head)
        val tag = if (inlineWait != null) "wait" else head
        val argument = inlineWait?.groupValues?.get(1) ?: rest.substring(rest.indexOf(']') + 1).trim()
        var action = build(tag, argument)
        if (delay != null) action = DelayAction(delay, listOf(action))
        if (chance != null) {
            action = ConditionalAction(allChecks = listOf(ChanceCondition(chance / 100.0)), thenActions = listOf(action))
        }
        return action
    }

    /**
     * Writes [action] as a line, or returns `null` when no line can describe it.
     *
     * An action is written as a line only when it matches what [parse] builds.
     */
    fun format(action: Action): String? = when (action) {
        is MessageAction -> textTag(action)
        is ActionBarAction -> if (action.serializerType == null && action.target == ActionTarget.PLAYER) {
            "[actionbar] ${action.text}"
        } else null
        is CommandAction -> "[${if (action.runner == CommandRunner.CONSOLE) "console" else "player"}] ${action.command}"
        is ConsoleLogAction -> if (action.level == LogLevel.INFO) "[log] ${action.text}" else null
        is StopAction -> "[stop]"
        is WaitAction -> "[wait ${action.duration.toMillis() / TICK_MILLIS}]"
        is RunAction -> "[run] ${action.name}"
        is TitleAction -> titleLine(action)
        else -> null
    }

    private fun titleLine(action: TitleAction): String? {
        if (action.serializerType != null) return null
        val tag = if (action.target == ActionTarget.ALL_PLAYERS) "broadcasttitle" else "title"
        val ticks = listOf(action.fadeIn, action.stay, action.fadeOut).map { it.toMillis() / TICK_MILLIS }
        return "[$tag] ${action.title};${action.subtitle};${ticks.joinToString(";")}"
    }

    private fun build(tag: String, argument: String): Action = when (tag) {
        "message" -> MessageAction(listOf(argument))
        "broadcast" -> MessageAction(listOf(argument), target = ActionTarget.ALL_PLAYERS)
        "minimessage" -> MessageAction(listOf(argument), ComponentSerializerType.MINI_MESSAGE)
        "minibroadcast" -> MessageAction(listOf(argument), ComponentSerializerType.MINI_MESSAGE, ActionTarget.ALL_PLAYERS)
        "json" -> MessageAction(listOf(argument), ComponentSerializerType.ADVENTURE_JSON)
        "jsonbroadcast" -> MessageAction(listOf(argument), ComponentSerializerType.ADVENTURE_JSON, ActionTarget.ALL_PLAYERS)
        "actionbar" -> ActionBarAction(argument)
        "player", "commandevent" -> CommandAction(argument, CommandRunner.PLAYER)
        "console" -> CommandAction(argument, CommandRunner.CONSOLE)
        "sound" -> sound(argument, ActionTarget.PLAYER)
        "broadcastsound" -> sound(argument, ActionTarget.ALL_PLAYERS)
        "log" -> ConsoleLogAction(argument)
        "stop" -> StopAction()
        "wait" -> WaitAction(parseDelay(argument))
        "run" -> RunAction(argument.also { require(it.isNotEmpty()) { "A run action needs a template name: [run] name" } })
        "title" -> title(argument, ActionTarget.PLAYER)
        "broadcasttitle" -> title(argument, ActionTarget.ALL_PLAYERS)
        else -> throw IllegalArgumentException(unknownTag(tag))
    }

    private val tags = listOf(
        "message", "broadcast", "minimessage", "minibroadcast", "json", "jsonbroadcast", "actionbar",
        "player", "commandevent", "console", "sound", "broadcastsound", "log", "stop", "wait", "run", "title", "broadcasttitle",
    )

    private fun unknownTag(tag: String): String {
        val hint = Suggestions.closest(tag, tags)?.let { " Did you mean [$it]?" }.orEmpty()
        return "Unknown action tag [$tag].$hint Tags: ${tags.joinToString { "[$it]" }}."
    }

    private fun title(argument: String, target: ActionTarget): Action {
        val parts = argument.split(';')
        require(parts.size <= 5) { "A title is written as: title;subtitle;fadeIn;stay;fadeOut (times in ticks)" }
        fun ticks(index: Int, default: Duration): Duration {
            val text = parts.getOrNull(index)?.trim().orEmpty()
            if (text.isEmpty()) return default
            val amount = text.toLongOrNull() ?: throw IllegalArgumentException("Invalid title time '$text': write ticks such as 20")
            require(amount >= 0) { "Title time must not be negative" }
            return Duration.ofMillis(amount * TICK_MILLIS)
        }
        val defaults = TitleAction()
        return TitleAction(
            parts[0].trim(),
            parts.getOrNull(1)?.trim().orEmpty(),
            ticks(2, defaults.fadeIn),
            ticks(3, defaults.stay),
            ticks(4, defaults.fadeOut),
            target = target,
        )
    }

    private fun sound(argument: String, target: ActionTarget): Action {
        val parts = argument.split(Regex("\\s+")).filter(String::isNotEmpty)
        require(parts.isNotEmpty() && parts.size <= 3) { "A sound is written as: name volume pitch" }
        val volume = parts.getOrNull(1)?.let { it.toFloatOrNull() ?: throw IllegalArgumentException("Invalid sound volume '$it'") } ?: 1f
        val pitch = parts.getOrNull(2)?.let { it.toFloatOrNull() ?: throw IllegalArgumentException("Invalid sound pitch '$it'") } ?: 1f
        return SoundAction(soundKey(parts[0]), volume = volume, pitch = pitch, target = target)
    }

    /** Turns `ENTITY_VILLAGER_NO` into `entity.villager.no`; keys that already have a `:` or `.` stay as written. */
    private fun soundKey(name: String): String =
        if (':' in name || '.' in name) name.lowercase(Locale.ROOT) else name.lowercase(Locale.ROOT).replace('_', '.')

    private fun parseDelay(value: String): Duration {
        val seconds = value.removeSuffix("s").trim()
        val amount = seconds.toLongOrNull()
            ?: throw IllegalArgumentException("Invalid delay '$value': write ticks such as 20, or seconds such as 2s")
        require(amount >= 0) { "Delay must not be negative" }
        return if (value.endsWith("s")) Duration.ofSeconds(amount) else Duration.ofMillis(amount * TICK_MILLIS)
    }

    private fun parseChance(value: String): Double {
        val percent = value.removeSuffix("%").trim().toDoubleOrNull()
            ?: throw IllegalArgumentException("Invalid chance '$value': write a percent from 0 to 100")
        require(percent in 0.0..100.0) { "Chance must be from 0 to 100" }
        return percent
    }

    private fun textTag(action: MessageAction): String? {
        if (action.messages.size != 1) return null
        val text = action.messages[0]
        val all = action.target == ActionTarget.ALL_PLAYERS
        return when (action.serializerType) {
            null -> "[${if (all) "broadcast" else "message"}] $text"
            ComponentSerializerType.MINI_MESSAGE -> "[${if (all) "minibroadcast" else "minimessage"}] $text"
            ComponentSerializerType.ADVENTURE_JSON -> "[${if (all) "jsonbroadcast" else "json"}] $text"
            else -> null
        }
    }
}
