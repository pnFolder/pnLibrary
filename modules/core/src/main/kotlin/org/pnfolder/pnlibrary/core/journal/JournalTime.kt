package org.pnfolder.pnlibrary.core.journal

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import org.pnfolder.pnlibrary.api.chat.ChatKit
import org.pnfolder.pnlibrary.api.chat.ChatPalette
import org.pnfolder.pnlibrary.common.text.RussianText
import org.pnfolder.pnlibrary.core.ui.DisplayZone
import java.time.format.DateTimeFormatter
import java.util.Locale

// Dates and times of the journal, always in the configured display zone (DisplayZone).

internal val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM HH:mm:ss")
internal val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
internal val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM")
internal val DAY_WITH_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

internal val TIME_WITH_YEAR: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")

/** Day and time, with the year only when it is not the current one, so old events are never ambiguous. */
internal fun stamp(millis: Long): String {
    val instant = java.time.Instant.ofEpochMilli(millis)
    val zone = DisplayZone.zone
    val sameYear = instant.atZone(zone).year == java.time.LocalDate.now(zone).year
    return (if (sameYear) TIME else TIME_WITH_YEAR).withZone(zone).format(instant)
}
internal val RU = Locale("ru")
internal val LONG_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM", RU)
internal val LONG_DAY_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy 'года'", RU)
internal val WEEKDAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE", RU)
internal val UTC_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(java.time.ZoneOffset.UTC)

/** "16 октября 2023 года", or "16 октября" within the current year. */
internal fun longDate(date: java.time.LocalDate): String =
    if (date.year == java.time.LocalDate.now(DisplayZone.zone).year) LONG_DAY.format(date) else LONG_DAY_YEAR.format(date)

internal fun localDate(millis: Long): java.time.LocalDate = java.time.Instant.ofEpochMilli(millis).atZone(DisplayZone.zone).toLocalDate()

/** "16 октября 2023 года, 16:45:07": the date in words, then the clock in the display zone. */
internal fun fullStamp(millis: Long): String =
    "${longDate(localDate(millis))}, ${CLOCK.withZone(DisplayZone.zone).format(java.time.Instant.ofEpochMilli(millis))}"

/** The same moment as numbers and in UTC, for the hover card, so the exact value is always one hover away. */
internal fun momentTip(kit: ChatKit, millis: Long) = kit.tooltip(
    kit.text(TIME_WITH_YEAR.withZone(DisplayZone.zone).format(java.time.Instant.ofEpochMilli(millis)), ChatPalette.TEXT),
    kit.text(WEEKDAY.format(localDate(millis)), ChatPalette.LABEL),
    kit.blank(),
    kit.hoverField("UTC", kit.text(UTC_STAMP.format(java.time.Instant.ofEpochMilli(millis)), ChatPalette.TEXT)),
    kit.hoverField("Часовой пояс", kit.text(zoneLabel(), ChatPalette.TEXT)),
    kit.hoverField("Прошло", kit.text(ago(millis).removeSuffix(" назад"), agoColor(millis))),
)

/** A date and time in words that shows the exact numbers when hovered. */
internal fun moment(kit: ChatKit, millis: Long): Component =
    kit.text(fullStamp(millis), ChatPalette.TEXT).hoverEvent(momentTip(kit, millis))

/** "How long ago": gray, and dimmer once it is more than a month old. */
internal fun agoColor(millis: Long, now: Long = System.currentTimeMillis()): TextColor =
    if (now - millis < 30L * 86_400_000) ChatPalette.SOFT else ChatPalette.FAINT
/**
 * How long ago [millis] was, with the two largest units: "только что", "5 мин назад", "3 ч 12 мин назад",
 * "5 дней назад", "3 месяца 2 дня назад" or "3 года 1 день назад".
 */
internal fun ago(millis: Long, now: Long = System.currentTimeMillis(), zone: java.time.ZoneId = DisplayZone.zone): String {
    val seconds = ((now - millis) / 1000).coerceAtLeast(0)
    if (seconds < 60) return "только что"
    if (seconds < 86_400) {
        val hours = seconds / 3600
        val minutes = seconds % 3600 / 60
        return (if (hours == 0L) "$minutes мин" else if (minutes == 0L) "$hours ч" else "$hours ч $minutes мин") + " назад"
    }
    val from = java.time.Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    val to = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val period = java.time.Period.between(from, to)
    val parts = listOf(
        period.years.toLong() to Triple("год", "года", "лет"),
        period.months.toLong() to Triple("месяц", "месяца", "месяцев"),
        period.days.toLong() to Triple("день", "дня", "дней"),
    ).filter { it.first > 0 }.take(2).map { (count, forms) -> "$count ${RussianText.plural(count, forms.first, forms.second, forms.third)}" }
    return (parts.joinToString(" ").ifEmpty { "1 день" }) + " назад"
}

/** A span such as "1 ч 20 мин" for the average gap between repeats. */
internal fun span(millis: Long): String {
    val minutes = millis / 60_000
    return when {
        minutes < 1 -> "меньше минуты"
        minutes < 60 -> "$minutes мин"
        minutes < 1440 -> "${minutes / 60} ч ${minutes % 60} мин"
        else -> "${minutes / 1440} д ${minutes % 1440 / 60} ч"
    }
}
/** The display zone as `Europe/Moscow (UTC+03:00)`, so nobody has to guess which clock a time uses. */
internal fun zoneLabel(): String {
    val zone = DisplayZone.zone
    val offset = zone.rules.getOffset(java.time.Instant.now()).id.let { if (it == "Z") "+00:00" else it }
    return "${zone.id} (UTC$offset)"
}
