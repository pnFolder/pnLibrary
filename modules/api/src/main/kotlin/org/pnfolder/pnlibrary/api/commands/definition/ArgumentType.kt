package org.pnfolder.pnlibrary.api.commands.definition

import org.pnfolder.pnlibrary.api.commands.error.CommandError
import org.pnfolder.pnlibrary.api.commands.error.InvalidArgument
import org.pnfolder.pnlibrary.api.commands.error.PlayerNotFound
import org.pnfolder.pnlibrary.api.audience.OnlinePlayer
import java.math.BigDecimal
import java.util.Locale

/** Converts one command token into a typed value. */
fun interface ArgumentType<T : Any> {
    /** Parses one raw command token, returning `null` when it is invalid. */
    fun parse(value: String): T?

    /**
     * Describes the accepted tokens for platforms that validate and suggest them natively. Platforms without
     * native support ignore it; the portable [parse] always decides in the end.
     */
    val spec: ArgumentSpec get() = ArgumentSpec.TEXT

    /**
     * The error reported when [parse] returns `null`: by default an [InvalidArgument]. A player argument answers
     * [PlayerNotFound], and a type of your own can return an error class of its own, such as `UnknownItem(value)`,
     * so that it gets its own answer.
     *
     * @param usage the usage hint of the command
     * @param argument the name of the argument that failed
     * @param value what was typed
     */
    fun error(usage: String, argument: String, value: String): CommandError = InvalidArgument(usage, argument, value)

    /** Built-in argument parsers and parser factories. */
    companion object {
        private fun <T : Any> typed(described: ArgumentSpec, parser: (String) -> T?): ArgumentType<T> = object : ArgumentType<T> {
            override fun parse(value: String): T? = parser(value)
            override val spec: ArgumentSpec get() = described
        }

        private val STRING = typed(ArgumentSpec.TEXT) { it }
        private val GREEDY_STRING = typed(ArgumentSpec.GREEDY) { it.takeIf(String::isNotBlank) }
        private val INTEGER = typed(ArgumentSpec.integer()) { it.toIntOrNull() }
        private val LONG = typed(ArgumentSpec.long()) { it.toLongOrNull() }
        private val DECIMAL = typed(ArgumentSpec.decimal()) { it.toBigDecimalOrNull() }
        private val BOOLEAN = typed(ArgumentSpec.BOOLEAN) { value ->
            when (value.lowercase(Locale.ROOT)) {
                "true", "yes", "on", "1" -> true
                "false", "no", "off", "0" -> false
                else -> null
            }
        }

        /** Returns the parser that preserves the input token as a string. */
        @JvmStatic
        fun string(): ArgumentType<String> = STRING

        /**
         * Returns the argument that takes the rest of the line, such as a clan name or a search phrase with spaces. It
         * must be the last node of its branch: the router hands it every remaining word joined by single spaces.
         */
        @JvmStatic
        fun greedyString(): ArgumentType<String> = GREEDY_STRING

        /** Returns the base-10 32-bit integer parser. */
        @JvmStatic
        fun integer(): ArgumentType<Int> = INTEGER

        /** Returns the 32-bit integer parser that accepts only values from [minimum] to [maximum]. */
        @JvmStatic
        fun integer(minimum: Int, maximum: Int): ArgumentType<Int> {
            require(minimum <= maximum) { "minimum must not exceed maximum" }
            return typed(ArgumentSpec.integer(minimum, maximum)) { token -> token.toIntOrNull()?.takeIf { it in minimum..maximum } }
        }

        /** Returns the base-10 64-bit integer parser. */
        @JvmStatic
        fun long(): ArgumentType<Long> = LONG

        /** Returns the 64-bit integer parser that accepts only values from [minimum] to [maximum]. */
        @JvmStatic
        fun long(minimum: Long, maximum: Long): ArgumentType<Long> {
            require(minimum <= maximum) { "minimum must not exceed maximum" }
            return typed(ArgumentSpec.long(minimum, maximum)) { token -> token.toLongOrNull()?.takeIf { it in minimum..maximum } }
        }

        /** Returns the arbitrary-precision decimal parser. */
        @JvmStatic
        fun decimal(): ArgumentType<BigDecimal> = DECIMAL

        /** Returns the decimal parser that accepts only values from [minimum] to [maximum]. */
        @JvmStatic
        fun decimal(minimum: Double, maximum: Double): ArgumentType<BigDecimal> {
            require(minimum <= maximum) { "minimum must not exceed maximum" }
            return typed(ArgumentSpec.decimal(minimum, maximum)) { token ->
                token.toBigDecimalOrNull()?.takeIf { it.toDouble() in minimum..maximum }
            }
        }

        /** Returns a case-insensitive boolean parser accepting common aliases. */
        @JvmStatic
        fun boolean(): ArgumentType<Boolean> = BOOLEAN

        /** Returns a case-insensitive parser for constants declared by enum [type]. */
        @JvmStatic
        fun <E : Enum<E>> enumeration(type: Class<E>): ArgumentType<E> =
            typed(ArgumentSpec.choice(type.enumConstants.map { it.name.lowercase(Locale.ROOT) })) { value ->
                type.enumConstants.firstOrNull { it.name.equals(value, ignoreCase = true) }
            }

        /**
         * Returns an argument that finds an online player by the typed name with [lookup]. A name nobody answers to
         * is reported as [PlayerNotFound], which the library answers with "player is not
         * online" unless a handler says otherwise.
         */
        @JvmStatic
        fun player(lookup: (String) -> OnlinePlayer?): ArgumentType<OnlinePlayer> = object : ArgumentType<OnlinePlayer> {
            override fun parse(value: String): OnlinePlayer? = lookup(value)
            override fun error(usage: String, argument: String, value: String): CommandError = PlayerNotFound(usage, argument, value)
        }

        /**
         * Returns a case-insensitive parser for one of [values]; the parsed value is the spelling declared here.
         * Platforms with native commands show the values as suggestions and reject anything else early.
         */
        @JvmStatic
        fun choice(vararg values: String): ArgumentType<String> {
            require(values.isNotEmpty()) { "choice needs at least one value" }
            val declared = values.toList()
            return typed(ArgumentSpec.choice(declared)) { token -> declared.firstOrNull { it.equals(token, ignoreCase = true) } }
        }
    }
}
