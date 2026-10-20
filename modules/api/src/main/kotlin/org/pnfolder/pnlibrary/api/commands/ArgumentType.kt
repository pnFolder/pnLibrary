package org.pnfolder.pnlibrary.api.commands

import org.pnfolder.pnlibrary.api.actions.LibraryPlayer
import java.math.BigDecimal
import java.util.Locale

/**
 * What a platform needs to know about an [ArgumentType] to validate and suggest it natively, such as the
 * Brigadier tree of a modern Paper server, before the portable parser runs.
 *
 * @property kind the shape of the accepted token
 * @property minimum lowest accepted number for numeric kinds, or `null` for no bound
 * @property maximum highest accepted number for numeric kinds, or `null` for no bound
 * @property choices the accepted values of a [Kind.CHOICE], also offered as suggestions
 */
class ArgumentSpec private constructor(
    val kind: Kind,
    val minimum: Double?,
    val maximum: Double?,
    choices: List<String>,
) {
    /** The shape of a token. */
    enum class Kind { TEXT, INTEGER, LONG, DECIMAL, BOOLEAN, CHOICE }

    val choices: List<String> = choices.toList()

    companion object {
        /** Any single word. */
        @JvmField
        val TEXT: ArgumentSpec = ArgumentSpec(Kind.TEXT, null, null, emptyList())

        /** A true/false token. */
        @JvmField
        val BOOLEAN: ArgumentSpec = ArgumentSpec(Kind.BOOLEAN, null, null, emptyList())

        /** A whole number between [minimum] and [maximum]. */
        @JvmStatic
        @JvmOverloads
        fun integer(minimum: Int = Int.MIN_VALUE, maximum: Int = Int.MAX_VALUE): ArgumentSpec =
            ArgumentSpec(Kind.INTEGER, minimum.toDouble(), maximum.toDouble(), emptyList())

        /** A 64-bit whole number between [minimum] and [maximum]. */
        @JvmStatic
        @JvmOverloads
        fun long(minimum: Long = Long.MIN_VALUE, maximum: Long = Long.MAX_VALUE): ArgumentSpec =
            ArgumentSpec(Kind.LONG, minimum.toDouble(), maximum.toDouble(), emptyList())

        /** A decimal number between [minimum] and [maximum]. */
        @JvmStatic
        @JvmOverloads
        fun decimal(minimum: Double = -Double.MAX_VALUE, maximum: Double = Double.MAX_VALUE): ArgumentSpec =
            ArgumentSpec(Kind.DECIMAL, minimum, maximum, emptyList())

        /** One of [choices]. */
        @JvmStatic
        fun choice(choices: List<String>): ArgumentSpec = ArgumentSpec(Kind.CHOICE, null, null, choices)
    }
}

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
     * The kind of problem reported when [parse] returns `null`. The default is
     * [CommandProblemKind.INVALID_ARGUMENT]; a player argument says [CommandProblemKind.PLAYER_NOT_FOUND], and a
     * type of your own can name a [CommandProblemKind.custom] kind so that it gets its own answer.
     */
    val problem: CommandProblemKind get() = CommandProblemKind.INVALID_ARGUMENT

    /** Built-in argument parsers and parser factories. */
    companion object {
        private fun <T : Any> typed(described: ArgumentSpec, parser: (String) -> T?): ArgumentType<T> = object : ArgumentType<T> {
            override fun parse(value: String): T? = parser(value)
            override val spec: ArgumentSpec get() = described
        }

        private val STRING = typed(ArgumentSpec.TEXT) { it }
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
         * is reported as [CommandProblemKind.PLAYER_NOT_FOUND], which the library answers with "player is not
         * online" unless a handler says otherwise.
         */
        @JvmStatic
        fun player(lookup: (String) -> LibraryPlayer?): ArgumentType<LibraryPlayer> = object : ArgumentType<LibraryPlayer> {
            override fun parse(value: String): LibraryPlayer? = lookup(value)
            override val problem: CommandProblemKind get() = CommandProblemKind.PLAYER_NOT_FOUND
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
