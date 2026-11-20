package org.pnfolder.pnlibrary.core.menu.style

import org.pnfolder.pnlibrary.common.time.DisplayZone
import org.pnfolder.pnlibrary.core.text.BAR_WIDTH
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val HOURS_PER_DAY = 24
private const val MINUTES_PER_HOUR = 60
private const val SECONDS_PER_MINUTE = 60L

internal fun timeLeft(span: Duration): String {
    val days = span.toDays()
    val hours = span.toHours() % HOURS_PER_DAY
    val minutes = span.toMinutes() % MINUTES_PER_HOUR
    return when {
        days > 0 -> "$days д $hours ч"
        hours > 0 -> "$hours ч $minutes мин"
        else -> "${minutes.coerceAtLeast(1)} мин"
    }
}

internal fun clockTime(moment: Instant): String = format(moment, "HH:mm")

internal fun clockStamp(moment: Instant): String = format(moment, "HH:mm:ss")

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
        span.isNegative -> "через ${timeLeft(span.negated())}"
        else -> "${timeLeft(span)} назад"
    }
}

internal fun progressBar(value: Long, busiest: Long): String {
    val width = ((value * BAR_WIDTH + busiest - 1) / busiest.coerceAtLeast(1)).toInt().coerceIn(1, BAR_WIDTH)
    return "█".repeat(width)
}
