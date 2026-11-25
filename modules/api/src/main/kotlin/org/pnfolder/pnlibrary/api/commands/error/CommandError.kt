package org.pnfolder.pnlibrary.api.commands.error

import org.pnfolder.pnlibrary.api.commands.CommandContext

/**
 * Anything that stops a command: a sender without permission, a wrong argument, a player who is not online, or an
 * error of your own such as `class NoMoney(val price: Int) : CommandError()`.
 *
 * An error is an ordinary class, so a handler receives it with its real fields instead of a map of strings, and a
 * handler for a base class also answers every error derived from it: `onError<BadUsage>` answers a wrong argument,
 * an unknown word and an unfinished command alike, until something more specific is set.
 *
 * Handlers are attached at five levels, and the most specific level that has one wins: a subcommand, a command, a
 * plugin module, the whole plugin, and last the library's standard answer from the library's settings.
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

/** Stops the command with [error]; never returns. */
@Suppress("unused")
fun CommandContext.fail(error: CommandError): Nothing = throw CommandException(error)

/**
 * Stops the command with the error made by [error] unless [condition] holds, the way `require` and `check` do:
 * `ensure(balance >= price) { NoMoney(price) }`. [error] is built only when it is needed.
 */
inline fun CommandContext.ensure(condition: Boolean, error: () -> CommandError) {
    if (!condition) throw CommandException(error())
}
