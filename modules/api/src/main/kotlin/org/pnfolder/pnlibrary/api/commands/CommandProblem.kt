package org.pnfolder.pnlibrary.api.commands

import java.util.Locale

/**
 * The reason a command could not run as typed. Every kind can be answered by its own handler on any of five levels:
 * a subcommand, a command, a module, a whole plugin, and the library's standard answer from `config.yml`. The most
 * specific level that has an answer wins.
 *
 * Kinds form small families: a kind without a handler on a level is answered by its [parent]'s handler there, so
 * `onUsage` also catches an invalid argument unless something more specific was set. Plugins declare their own
 * kinds with [custom] and raise them from a command with [CommandProblemException].
 *
 * @property id stable name such as `pnlibrary:denied` or `shop:no-money`
 * @property parent the kind whose handler answers when this kind has none, or `null`
 */
class CommandProblemKind private constructor(val id: String, val parent: CommandProblemKind?) {
    /** This kind followed by its parents, most specific first. */
    val lineage: List<CommandProblemKind> get() = generateSequence(this) { it.parent }.toList()

    override fun equals(other: Any?): Boolean = other is CommandProblemKind && other.id == id

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = id

    companion object {
        /** Something failed that no other kind describes, including an unexpected error in a command. */
        @JvmField
        val FAILED: CommandProblemKind = CommandProblemKind("pnlibrary:failed", null)

        /** The sender may not use the command or subcommand. Details: `permission`. */
        @JvmField
        val DENIED: CommandProblemKind = CommandProblemKind("pnlibrary:denied", FAILED)

        /** The command was typed wrongly. Details: `usage`. The parent of the three kinds below. */
        @JvmField
        val USAGE: CommandProblemKind = CommandProblemKind("pnlibrary:usage", FAILED)

        /** A word the command does not know, or one too many. Details: `usage`, `value`. */
        @JvmField
        val UNKNOWN_ARGUMENT: CommandProblemKind = CommandProblemKind("pnlibrary:unknown-argument", USAGE)

        /** The command stopped before a required part. Details: `usage`. */
        @JvmField
        val INCOMPLETE: CommandProblemKind = CommandProblemKind("pnlibrary:incomplete", USAGE)

        /** An argument that does not parse. Details: `usage`, `argument`, `value`. */
        @JvmField
        val INVALID_ARGUMENT: CommandProblemKind = CommandProblemKind("pnlibrary:invalid-argument", USAGE)

        /** A player argument that names nobody online. Details: `usage`, `argument`, `value`. */
        @JvmField
        val PLAYER_NOT_FOUND: CommandProblemKind = CommandProblemKind("pnlibrary:player-not-found", INVALID_ARGUMENT)

        /** The command needs a player and the sender is the console. */
        @JvmField
        val PLAYER_ONLY: CommandProblemKind = CommandProblemKind("pnlibrary:player-only", FAILED)

        /** The command needs the console and the sender is a player. */
        @JvmField
        val CONSOLE_ONLY: CommandProblemKind = CommandProblemKind("pnlibrary:console-only", FAILED)

        /** The sender used the command again too soon. */
        @JvmField
        val COOLDOWN: CommandProblemKind = CommandProblemKind("pnlibrary:cooldown", FAILED)

        private val ID = Regex("[a-z0-9_.-]+:[a-z0-9_./-]+")

        /**
         * Declares a kind of your own, such as `custom("shop:no-money")`. Raise it with [CommandProblemException] and
         * answer it with `onProblem` at any level. Without a handler it falls back to [parent].
         *
         * @param id `namespace:name` in lower case
         * @param parent the kind that answers when this one has no handler
         */
        @JvmStatic
        @JvmOverloads
        fun custom(id: String, parent: CommandProblemKind = FAILED): CommandProblemKind {
            val normalized = id.trim().lowercase(Locale.ROOT)
            require(ID.matches(normalized)) { "Problem kind must look like namespace:name, got '$id'" }
            return CommandProblemKind(normalized, parent)
        }
    }
}

/**
 * One problem handed to a handler.
 *
 * @property kind what went wrong
 * @property context who ran which command; the sender can be messaged, played a sound, or anything else
 * @property details facts about the problem, such as `usage`, `argument`, `value` or `permission`
 */
class CommandProblem(
    val kind: CommandProblemKind,
    val context: CommandContext,
    val details: Map<String, Any?> = emptyMap(),
) {
    /** The sender, shorthand for `context.sender`. */
    val sender: CommandSender get() = context.sender

    /** Returns the detail named [key] as text, or an empty string when it is missing. */
    fun detail(key: String): String = details[key]?.toString().orEmpty()

    /** Fills every `{name}` in [template] with the detail of that name, so `"{value} is not a number"` reads well. */
    fun text(template: String): String =
        PLACEHOLDER.replace(template) { match -> details[match.groupValues[1]]?.toString() ?: match.value }

    private companion object {
        val PLACEHOLDER = Regex("\\{([a-zA-Z0-9_.-]+)}")
    }
}

/** Answers a [CommandProblem]: message the sender, play a sound, show an action bar, log it, tell the staff. */
fun interface CommandProblemHandler {
    /** Called with the problem; throwing here is logged and the next level answers instead. */
    fun handle(problem: CommandProblem)
}

/**
 * Throw this from a command to report a problem of your own or of a built-in kind. It is answered like any other
 * problem, through the five levels, instead of showing an error.
 *
 * ```kotlin
 * val noMoney = CommandProblemKind.custom("shop:no-money")
 * executes { if (!pay(it)) throw CommandProblemException(noMoney, mapOf("price" to 50)) }
 * ```
 */
class CommandProblemException @JvmOverloads constructor(
    val kind: CommandProblemKind,
    val details: Map<String, Any?> = emptyMap(),
) : RuntimeException(kind.id, null, false, false)
