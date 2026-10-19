package org.pnfolder.pnlibrary.core.placeholders

import org.pnfolder.pnlibrary.common.text.RussianText
import java.time.Duration

/** Stateless implementations of pnLibrary's built-in placeholder formatters. */
internal object PlaceholderBuiltInFormatters {
    /** Selects singular, paucal, or plural text using Russian-style integer rules. */
    fun plural(value: Any, variants: List<String>): String {
        if (variants.size < 3) return variants.firstOrNull().orEmpty()
        return RussianText.plural((value as Number).toLong(), variants[0], variants[1], variants[2])
    }

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
