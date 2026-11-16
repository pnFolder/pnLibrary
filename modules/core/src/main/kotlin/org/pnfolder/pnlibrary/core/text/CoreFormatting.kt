package org.pnfolder.pnlibrary.core.text

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

internal const val NANOS_PER_MICRO = 1_000L
internal const val NANOS_PER_MILLI = 1_000_000L
internal const val BAR_WIDTH = 12
internal const val WRAP_WIDTH = 44
internal val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM", Locale("ru"))
private val DAY_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ru"))
private const val WRAP_LINES = 8

internal fun shortName(name: String): String = name.substringAfterLast('.')

internal fun nanosText(nanos: Long): String =
    if (nanos >= NANOS_PER_MILLI) "%.1f мс".format(nanos / NANOS_PER_MILLI.toDouble())
    else "%.1f мкс".format(nanos / NANOS_PER_MICRO.toDouble())

internal fun dayTitle(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Сегодня"
    today.minusDays(1) -> "Вчера"
    else -> (if (date.year == today.year) DAY else DAY_YEAR).format(date)
}

internal fun wrapText(text: String, width: Int = WRAP_WIDTH, limit: Int = WRAP_LINES): List<String> {
    val lines = ArrayList<String>()
    for (raw in text.lines()) {
        var line = StringBuilder()
        for (word in raw.trim().split(' ')) {
            if (line.isNotEmpty() && line.length + 1 + word.length > width) {
                lines += line.toString()
                line = StringBuilder()
            }
            var rest = word
            while (rest.length > width) {
                lines += rest.take(width)
                rest = rest.drop(width)
            }
            if (line.isNotEmpty()) line.append(' ')
            line.append(rest)
        }
        lines += line.toString()
    }
    return if (lines.size > limit) lines.take(limit) + "…" else lines
}
