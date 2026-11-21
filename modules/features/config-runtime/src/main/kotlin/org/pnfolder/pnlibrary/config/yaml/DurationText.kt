package org.pnfolder.pnlibrary.config.yaml

import java.util.concurrent.TimeUnit

/** Reads a length of time typed as `1h30m`, `90 мин` or `45s` into the unit a setting keeps. */
internal object DurationText {
    private val PART = Regex("""(\d+(?:[.,]\d+)?)\s*(ms|мс|сек|с|s|мин|м|m|ч|h|д|d)""")

    /** The time in [unit], or `null` when [text] has no unit letters (a plain number) or [unit] is `null`. */
    fun parse(text: String, unit: TimeUnit?): Long? {
        if (unit == null) return null
        val cleaned = text.trim().lowercase()
        val parts = PART.findAll(cleaned).toList()
        if (parts.isEmpty() || parts.joinToString("") { it.value }.replace(" ", "") != cleaned.replace(" ", "")) return null
        val millis = parts.sumOf { part ->
            val amount = part.groupValues[1].replace(',', '.').toDouble()
            (amount * millisOf(part.groupValues[2])).toLong()
        }
        return unit.convert(millis, TimeUnit.MILLISECONDS)
    }

    private fun millisOf(letters: String): Long = when (letters) {
        "ms", "мс" -> 1L
        "с", "s", "сек" -> 1_000L
        "м", "m", "мин" -> 60_000L
        "ч", "h" -> 3_600_000L
        else -> 86_400_000L
    }
}
