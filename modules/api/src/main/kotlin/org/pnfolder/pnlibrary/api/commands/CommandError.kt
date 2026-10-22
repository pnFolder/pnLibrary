package org.pnfolder.pnlibrary.api.commands

import java.time.Duration

/**
 * Anything that stops a command: a sender without permission, a wrong argument, a player who is not online, or an
 * error of your own such as `class NoMoney(val price: Int) : CommandError()`.
 *
 * An error is an ordinary class, so a handler receives it with its real fields instead of a map of strings, and a
 * handler for a base class also answers every error derived from it: `onError<BadUsage>` answers a wrong argument,
 * an unknown word and an unfinished command alike, until something more specific is set.
 *
 * Handlers are attached at five levels, and the most specific level that has one wins: a subcommand, a command, a
 * plugin module, the whole plugin, and last the library's standard answer from `config.yml`.
 *
 * @property defaultMessage what the library says when no handler answers, or `null` to use the standard text of the
 * nearest built-in base class. It may contain `{name}` placeholders from [placeholders].
 */
open class CommandError @JvmOverloads constructor(val defaultMessage: String? = null) {
    /** Values that fill `{name}` in a message, such as `mapOf("price" to 50)` for `"Нужно {price} монет"`. */
    open fun placeholders(): Map<String, Any?> = emptyMap()

    /** Fills every `{name}` of [template] with the placeholder of that name; unknown names stay as written. */
    fun format(template: String): String {
        val values = placeholders()
        return PLACEHOLDER.replace(template) { match -> values[match.groupValues[1]]?.toString() ?: match.value }
    }

    private companion object {
        val PLACEHOLDER = Regex("\\{([a-zA-Z0-9_.-]+)}")
    }
}

/** The sender may not use the command or subcommand. [permission] is empty when an availability rule hid it instead. */
open class NoPermission(val permission: String) : CommandError() {
    override fun placeholders() = mapOf("permission" to permission)
}

/** The command was typed wrongly. [usage] is the generated hint, such as `/shop buy <item>`. */
open class BadUsage(val usage: String) : CommandError() {
    override fun placeholders(): Map<String, Any?> = mapOf("usage" to usage)
}

/** A word the command does not know, or one too many. */
open class UnknownArgument(usage: String, val value: String) : BadUsage(usage) {
    override fun placeholders(): Map<String, Any?> = super.placeholders() + ("value" to value)
}

/** The command stopped before a part it needs. */
open class IncompleteCommand(usage: String) : BadUsage(usage)

/** An argument that does not parse: [argument] is its name and [value] what was typed. */
open class InvalidArgument(usage: String, val argument: String, val value: String) : BadUsage(usage) {
    override fun placeholders(): Map<String, Any?> = super.placeholders() + mapOf("argument" to argument, "value" to value)
}

/** A player argument that names nobody online. */
open class PlayerNotFound(usage: String, argument: String, value: String) : InvalidArgument(usage, argument, value)

/** The command needs a player and the sender is the console. */
class PlayerOnly : CommandError()

/** The command needs the console and the sender is a player. */
class ConsoleOnly : CommandError()

/** The sender used the command again too soon; [remaining] is how long to wait. */
open class OnCooldown(val remaining: Duration) : CommandError() {
    override fun placeholders(): Map<String, Any?> =
        mapOf("seconds" to ((remaining.toMillis() + 999) / 1000), "remaining" to remaining)
}

/** The base of everything else that went wrong, and the last resort of the standard answers. */
open class CommandFailed @JvmOverloads constructor(defaultMessage: String? = null) : CommandError(defaultMessage)

/**
 * Stops the command with [error]. The library answers it through the five levels like any other error, so the
 * command does not print anything itself. Java callers may throw this directly.
 */
class CommandFailure(val error: CommandError) : RuntimeException(error.javaClass.simpleName, null, false, false)

/** Stops the command with [error]; never returns. */
@Suppress("unused")
fun CommandContext.fail(error: CommandError): Nothing = throw CommandFailure(error)

/**
 * Stops the command with the error made by [error] unless [condition] holds, the way `require` and `check` do:
 * `ensure(balance >= price) { NoMoney(price) }`. [error] is built only when it is needed.
 */
inline fun CommandContext.ensure(condition: Boolean, error: () -> CommandError) {
    if (!condition) throw CommandFailure(error())
}

/** Answers an error. `context` tells who ran which command; the sender can be messaged, played a sound, anything. */
fun interface CommandErrorHandler<E : CommandError> {
    /** Called with the error; throwing here is logged and the next level answers instead. */
    fun handle(context: CommandContext, error: E)
}
