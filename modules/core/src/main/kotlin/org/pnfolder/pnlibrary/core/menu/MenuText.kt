package org.pnfolder.pnlibrary.core.menu

import org.pnfolder.pnlibrary.common.time.DisplayZone
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun left(span: Duration): String {
    val days = span.toDays()
    val hours = span.toHours() % HOURS_PER_DAY
    val minutes = span.toMinutes() % MINUTES_PER_HOUR
    return when {
        days > 0 -> "$days д $hours ч"
        hours > 0 -> "$hours ч $minutes мин"
        else -> "${minutes.coerceAtLeast(1)} мин"
    }
}

internal fun duration(nanos: Long): String =
    if (nanos >= NANOS_PER_MILLI) "%.1f мс".format(nanos / NANOS_PER_MILLI.toDouble()) else "%.1f мкс".format(nanos / NANOS_PER_MICRO.toDouble())

internal fun shortName(name: String): String = name.substringAfterLast('.')

internal fun time(moment: Instant): String = format(moment, "HH:mm")

internal fun stamp(moment: Instant): String = format(moment, "HH:mm:ss")

private fun format(moment: Instant, clock: String): String {
    val zone = DisplayZone.zone
    val year = if (moment.atZone(zone).year == LocalDate.now(zone).year) "" else " yyyy"
    return DateTimeFormatter.ofPattern("d MMMM$year, $clock", Locale("ru")).withZone(zone).format(moment)
}

/** How long ago [moment] was, or how long until it: `только что`, `5 мин назад`, `через 2 ч 10 мин`. */
internal fun ago(moment: Instant): String {
    val span = Duration.between(moment, Instant.now())
    return when {
        span.abs().seconds < SECONDS_PER_MINUTE -> "только что"
        span.isNegative -> "через ${left(span.negated())}"
        else -> "${left(span)} назад"
    }
}

internal fun dayTitle(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Сегодня"
    today.minusDays(1) -> "Вчера"
    else -> (if (date.year == today.year) DAY else DAY_YEAR).format(date)
}

internal fun bar(value: Long, busiest: Long): String {
    val width = ((value * BAR_WIDTH + busiest - 1) / busiest.coerceAtLeast(1)).toInt().coerceIn(1, BAR_WIDTH)
    return "█".repeat(width)
}

internal fun wrap(text: String, width: Int = WRAP_WIDTH, limit: Int = WRAP_LINES): List<String> {
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

internal fun reason(error: Throwable): String {
    val cause = generateSequence(error) { it.cause }.last()
    return cause.message ?: cause.javaClass.simpleName
}
