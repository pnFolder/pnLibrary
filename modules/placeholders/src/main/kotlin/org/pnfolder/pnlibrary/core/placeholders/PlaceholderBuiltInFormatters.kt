package org.pnfolder.pnlibrary.core.placeholders

import org.pnfolder.pnlibrary.common.text.RussianText
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAccessor
import java.util.Locale

/** Stateless implementations of pnLibrary's built-in placeholder formatters. */
object PlaceholderBuiltInFormatters {
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

    /** Makes the first character upper case. */
    fun capitalize(value: Any): String =
        value.toString().replaceFirstChar { it.titlecase(Locale.ROOT) }

    /** Rounds and drops trailing zeros: `round:2` turns `3.10` into `3.1`. */
    fun round(value: Any, arguments: List<String>): String {
        val number = decimal(value) ?: return value.toString()
        val decimals = arguments.firstOrNull()?.trim()?.toIntOrNull()?.coerceIn(0, 12) ?: 0
        return number.setScale(decimals, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    }

    /** Shows a fraction as percent: `percent:1` turns `0.256` into `25.6%`. */
    fun percent(value: Any, arguments: List<String>): String {
        val number = decimal(value) ?: return value.toString()
        val decimals = arguments.firstOrNull()?.trim()?.toIntOrNull()?.coerceIn(0, 12) ?: 0
        return number.multiply(BigDecimal(100)).setScale(decimals, RoundingMode.HALF_UP).toPlainString() + "%"
    }

    /** Groups thousands and fixes the decimals: `money` turns `1234.5` into `1 234.50`; `money:0` drops the cents. */
    fun money(value: Any, arguments: List<String>): String {
        val number = decimal(value) ?: return value.toString()
        val decimals = arguments.firstOrNull()?.trim()?.toIntOrNull()?.coerceIn(0, 12) ?: 2
        val fixed = number.setScale(decimals, RoundingMode.HALF_UP).toPlainString()
        val whole = fixed.substringBefore('.')
        val sign = if (whole.startsWith("-")) "-" else ""
        val digits = whole.removePrefix("-").reversed().chunked(3).joinToString(" ").reversed()
        val fraction = fixed.substringAfter('.', "")
        return sign + digits + if (fraction.isEmpty()) "" else ".$fraction"
    }

    /** Pads on the left to a width: `pad:5,0` turns `42` into `00042`; the filler is a space by default. */
    fun pad(value: Any, arguments: List<String>): String {
        val width = arguments.firstOrNull()?.trim()?.toIntOrNull()?.coerceIn(0, 64) ?: return value.toString()
        val filler = arguments.getOrNull(1)?.firstOrNull() ?: ' '
        return value.toString().padStart(width, filler)
    }

    /** Makes every word start with a capital letter. */
    fun title(value: Any): String =
        value.toString().split(' ').joinToString(" ") { word -> word.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) } }

    /** Shows a moment, given as epoch milliseconds or a java.time value, in the pattern of the argument. */
    fun date(value: Any, arguments: List<String>): String {
        val instant = when (value) {
            is Instant -> value
            is Number -> Instant.ofEpochMilli(value.toLong())
            is TemporalAccessor -> return runCatching { formatter(arguments).format(value) }.getOrDefault(value.toString())
            else -> return value.toString()
        }
        return runCatching { formatter(arguments).format(instant.atZone(ZoneId.systemDefault())) }.getOrDefault(value.toString())
    }

    private fun formatter(arguments: List<String>): DateTimeFormatter =
        DateTimeFormatter.ofPattern(arguments.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_DATE)

    private const val DEFAULT_DATE = "dd.MM.yyyy HH:mm"

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
