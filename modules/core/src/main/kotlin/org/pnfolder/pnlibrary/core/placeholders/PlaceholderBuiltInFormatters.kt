package org.pnfolder.pnlibrary.core.placeholders

import org.pnfolder.pnlibrary.common.text.RussianText
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.util.Locale

/** Stateless implementations of pnLibrary's built-in placeholder formatters. */
internal object PlaceholderBuiltInFormatters {
    /** Selects singular, paucal, or plural text using Russian-style integer rules. */
    fun plural(value: Any, variants: List<String>): String {
        if (variants.size < 3) return variants.firstOrNull().orEmpty()
        return RussianText.plural((value as Number).toLong(), variants[0], variants[1], variants[2])
    }

    /** Fixes the number of decimals: `number:2` turns `3.14159` into `3.14`. Text that is not a number is kept. */
    fun number(value: Any, arguments: List<String>): String {
        val decimals = arguments.firstOrNull()?.trim()?.toIntOrNull()?.coerceIn(0, 12) ?: 0
        val number = decimal(value) ?: return value.toString()
        return number.setScale(decimals, RoundingMode.HALF_UP).toPlainString()
    }

    /** Groups the digits of the whole part by thousands: `1234567.5` becomes `1 234 567.5`. */
    fun group(value: Any): String {
        val number = decimal(value) ?: return value.toString()
        val text = number.stripTrailingZeros().toPlainString()
        val whole = text.substringBefore('.')
        val sign = if (whole.startsWith("-")) "-" else ""
        val digits = whole.removePrefix("-").reversed().chunked(3).joinToString(" ").reversed()
        val fraction = text.substringAfter('.', "")
        return sign + digits + if (fraction.isEmpty()) "" else ".$fraction"
    }

    /** Shortens large numbers: `1500` becomes `1.5k`, `2300000` becomes `2.3M`. */
    fun short(value: Any): String {
        val number = decimal(value) ?: return value.toString()
        val size = number.abs()
        val (divisor, suffix) = SHORT_UNITS.firstOrNull { size >= it.first } ?: return number.stripTrailingZeros().toPlainString()
        val scaled = number.divide(divisor, 1, RoundingMode.DOWN).stripTrailingZeros()
        return scaled.toPlainString() + suffix
    }

    /** Makes the first letter a capital one. */
    fun capitalize(value: Any): String =
        value.toString().replaceFirstChar { it.titlecase(Locale.ROOT) }

    private fun decimal(value: Any): BigDecimal? = when (value) {
        is BigDecimal -> value
        is Number -> value.toString().toBigDecimalOrNull()
        else -> value.toString().trim().toBigDecimalOrNull()
    }

    private val SHORT_UNITS = listOf(
        BigDecimal("1000000000000") to "T",
        BigDecimal("1000000000") to "B",
        BigDecimal("1000000") to "M",
        BigDecimal("1000") to "k",
    )

    /** Formats a [Duration] or numeric second count as compact day/hour/minute/second units. */
    fun duration(value: Any): String {
        var seconds = when (value) {
            is Duration -> value.seconds
            is Number -> value.toLong()
            else -> return value.toString()
        }
        val sign = if (seconds < 0) "-" else ""
        seconds = kotlin.math.abs(seconds)
        val days = seconds / 86_400
        seconds %= 86_400
        val hours = seconds / 3_600
        seconds %= 3_600
        val minutes = seconds / 60
        seconds %= 60
        val formatted = listOf(days to "d", hours to "h", minutes to "m", seconds to "s")
            .filter { it.first > 0 }
            .joinToString(" ") { "${it.first}${it.second}" }
            .ifEmpty { "0s" }
        return sign + formatted
    }
}
