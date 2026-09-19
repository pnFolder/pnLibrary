package ru.privatenull.pnlibrary.api.commands

import java.math.BigDecimal
import java.util.Locale

/** Converts one command token into a typed value. */
fun interface ArgumentType<T : Any> {
    fun parse(value: String): T?

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

        @JvmStatic fun string(): ArgumentType<String> = STRING
        @JvmStatic fun integer(): ArgumentType<Int> = INTEGER
        @JvmStatic fun long(): ArgumentType<Long> = LONG
        @JvmStatic fun decimal(): ArgumentType<BigDecimal> = DECIMAL
        @JvmStatic fun boolean(): ArgumentType<Boolean> = BOOLEAN

        @JvmStatic
        fun <E : Enum<E>> enumeration(type: Class<E>): ArgumentType<E> = ArgumentType { value ->
            type.enumConstants.firstOrNull { it.name.equals(value, ignoreCase = true) }
        }
    }
}
