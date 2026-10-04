package ru.privatenull.pnlibrary.api.commands

import java.math.BigDecimal
import java.util.Locale

/** Converts one command token into a typed value. */
fun interface ArgumentType<T : Any> {
    /** Parses one raw command token, returning `null` when it is invalid. */
    fun parse(value: String): T?

    /** Built-in argument parsers and parser factories. */
    companion object {
        private val STRING = ArgumentType<String> { it }
        private val INTEGER = ArgumentType<Int> { it.toIntOrNull() }
        private val LONG = ArgumentType<Long> { it.toLongOrNull() }
        private val DECIMAL = ArgumentType<BigDecimal> { it.toBigDecimalOrNull() }
        private val BOOLEAN = ArgumentType<Boolean> { value ->
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

        /** Returns the base-10 64-bit integer parser. */
        @JvmStatic
        fun long(): ArgumentType<Long> = LONG

        /** Returns the arbitrary-precision decimal parser. */
        @JvmStatic
        fun decimal(): ArgumentType<BigDecimal> = DECIMAL

        /** Returns a case-insensitive boolean parser accepting common aliases. */
        @JvmStatic
        fun boolean(): ArgumentType<Boolean> = BOOLEAN

        /** Returns a case-insensitive parser for constants declared by enum [type]. */
        @JvmStatic
        fun <E : Enum<E>> enumeration(type: Class<E>): ArgumentType<E> = ArgumentType { value ->
            type.enumConstants.firstOrNull { it.name.equals(value, ignoreCase = true) }
        }
    }
}
