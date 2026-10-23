package org.pnfolder.pnlibrary.core.journal

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.TextColor
import org.pnfolder.pnlibrary.api.chat.ChatKit
import org.pnfolder.pnlibrary.api.chat.ChatPalette
import org.pnfolder.pnlibrary.common.text.RussianText
import org.pnfolder.pnlibrary.api.chat.ChatReader
import org.pnfolder.pnlibrary.core.ui.DisplayZone
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter

/** A group is "just now" when its last record is this recent. */
private val JUST_NOW: Duration = Duration.ofMinutes(10)

/** Records that appear this soon after the library started are reported as startup problems. */
private val STARTUP: Duration = Duration.ofMinutes(3)

/** From this many repeats within the window an event is called recurring. */
private const val RECURRING_FROM = 5

/** Notable events listed in the notice; the rest are in the journal. */
private const val MAX_NOTABLE = 2

/** Characters of one chat line, and the least we still show of a message. */
private const val CHAT_WIDTH = 52
private const val MIN_MESSAGE = 10

/** The activity strip always has this many columns, whatever the window is. */
private const val STRIP_COLUMNS = 24

/** Strip heights for 0, 1, 2-3, 4-7 and 8+ records in one column. */
private const val STRIP_HEIGHTS = " ▁▃▅▇"

/** Worst columns named in the strip's hover card. */
private const val STRIP_WORST = 3

/** Why an event is worth a look; the notice leads with the most urgent one. */
private enum class Reason(val word: String, val advice: String) {
    STARTUP("старт", "Проверьте, что плагин подходит к версии сервера."),
    JUST_NOW("только что", "Посмотрите, что делали игроки в это время."),
    RECURRING("повтор", "Покажите карточку автору плагина, там есть отчёт."),
    PLAIN("", ""),
}

/**
 * The short notice an administrator sees when joining: how many problems there were, when they happened (a strip of
 * the window), the one or two events worth a look with a word for why, one piece of advice and where to click.
 * Returns no lines when nothing needs attention, so a healthy server stays quiet.
 *
 * @param records warnings and errors of the window, any order
 * @param now current time in epoch milliseconds
 * @param windowHours length of the window, shown in the header
 * @param startedAtMillis when the library started, to recognize startup problems
 */
internal fun joinDigest(
    records: List<JournalRecord>,
    now: Long,
    windowHours: Int,
    startedAtMillis: Long,
    root: String = "/pn events",
): List<Component> {
    val kit = ChatKit(ChatReader())
    val relevant = records.filter { it.level != JournalLevel.INFO }
    val groups = relevant.groupBy { it.fingerprint }.values
        .map { it.sortedByDescending(JournalRecord::timeMillis) }
        .sortedByDescending { it.first().timeMillis }
    if (groups.isEmpty()) return emptyList()

    val errors = groups.filter { it.first().level == JournalLevel.ERROR }
    val warnings = groups.filter { it.first().level == JournalLevel.WARNING }
    val errorCount = errors.sumOf { it.size }
    val warningCount = warnings.sumOf { it.size }

    fun reasonOf(group: List<JournalRecord>): Reason {
        val first = group.last().timeMillis
        return when {
            first >= startedAtMillis - 1_000 && first <= startedAtMillis + STARTUP.toMillis() -> Reason.STARTUP
            group.first().timeMillis >= now - JUST_NOW.toMillis() -> Reason.JUST_NOW
            group.size >= RECURRING_FROM -> Reason.RECURRING
            else -> Reason.PLAIN
        }
    }

    // Most urgent first; errors before warnings inside one reason. If nothing stands out, the newest error (or warning).
    val ranked = groups.sortedWith(compareBy<List<JournalRecord>> { reasonOf(it).ordinal }
        .thenBy { if (it.first().level == JournalLevel.ERROR) 0 else 1 })
    val notable = ranked.take(MAX_NOTABLE)
    val topReason = reasonOf(ranked.first())
    // An event that keeps repeating is a repeating problem even when its last repeat was a moment ago.
    val adviceReason = if (topReason == Reason.JUST_NOW && ranked.first().size >= RECURRING_FROM) Reason.RECURRING else topReason

    val summary = mutableListOf<Component>()
    if (errors.isNotEmpty()) {
        summary += kit.text(kit.glyph('●') + " $errorCount ", ChatPalette.DANGER)
            .append(kit.text(RussianText.plural(errorCount.toLong(), "ошибка", "ошибки", "ошибок"), ChatPalette.SOFT))
    }
    if (warnings.isNotEmpty()) {
        summary += kit.text(kit.glyph('▲') + " $warningCount ", ChatPalette.WARN)
            .append(kit.text(RussianText.plural(warningCount.toLong(), "предупреждение", "предупреждения", "предупреждений"), ChatPalette.SOFT))
    }

    val lines = mutableListOf<Component>(
        kit.blank(),
        kit.header("Журнал", kit.text("за последние $windowHours ${RussianText.plural(windowHours.toLong(), "час", "часа", "часов")}", ChatPalette.LABEL)),
        summary.drop(1).fold(kit.text("  ").append(summary.first())) { line, part -> line.append(kit.text("   ")).append(part) },
        strip(kit, relevant, now, windowHours, startedAtMillis),
    )
    notable.forEach { lines += notableLine(kit, it, reasonOf(it), now, root) }
    if (topReason == Reason.PLAIN) {
        if (errors.isEmpty()) lines += kit.note("Ничего срочного. Загляните в журнал, когда будет время.", ChatPalette.FAINT)
    } else {
        lines += kit.note(adviceReason.advice, ChatPalette.FAINT)
    }
    lines += kit.blank()
    val urgent = errors.isNotEmpty() || topReason != Reason.PLAIN
    lines += kit.actions(
        kit.button("открыть журнал", if (errors.isNotEmpty()) "$root error" else "$root warning", "Список событий", ChatPalette.ACCENT),
        if (urgent) kit.button("подробнее", "$root view ${ranked.first().first().fingerprint}", "Карточка главного события", ChatPalette.ACCENT) else null,
    ) ?: kit.blank()
    return lines
}

private fun levelColor(level: JournalLevel): TextColor = when (level) {
    JournalLevel.ERROR -> ChatPalette.DANGER
    JournalLevel.WARNING -> ChatPalette.WARN
    JournalLevel.INFO -> ChatPalette.INFO
}

private val HOUR_MINUTE: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val DATE_CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM HH:mm")

private fun clock(millis: Long, withDate: Boolean): String =
    (if (withDate) DATE_CLOCK else HOUR_MINUTE).withZone(DisplayZone.zone).format(Instant.ofEpochMilli(millis))

/**
 * A strip of [STRIP_COLUMNS] columns over the window, oldest first: the height is how many problems fell into the
 * column, red when it holds an error, yellow when only warnings, orange for the column of the server start, and a
 * dim dot when it is quiet. Hovering names the worst columns.
 */
private fun strip(kit: ChatKit, relevant: List<JournalRecord>, now: Long, windowHours: Int, startedAtMillis: Long): Component {
    val windowMillis = Duration.ofHours(windowHours.toLong()).toMillis()
    val columnMillis = (windowMillis / STRIP_COLUMNS).coerceAtLeast(1)
    val columnOf = { millis: Long -> (STRIP_COLUMNS - 1 - (now - millis) / columnMillis).toInt().coerceIn(0, STRIP_COLUMNS - 1) }
    val errors = IntArray(STRIP_COLUMNS)
    val warnings = IntArray(STRIP_COLUMNS)
    relevant.forEach { if (it.level == JournalLevel.ERROR) errors[columnOf(it.timeMillis)]++ else warnings[columnOf(it.timeMillis)]++ }
    val startColumn = columnOf(startedAtMillis)

    var line = kit.text("  ")
    for (column in 0 until STRIP_COLUMNS) {
        val total = errors[column] + warnings[column]
        val height = when {
            total == 0 -> 0
            total == 1 -> 1
            total <= 3 -> 2
            total <= 7 -> 3
            else -> 4
        }
        val color = when {
            total == 0 -> ChatPalette.FAINT
            column == startColumn -> ChatPalette.ORANGE
            errors[column] > 0 -> ChatPalette.DANGER
            else -> ChatPalette.WARN
        }
        line = line.append(kit.text(if (height == 0) "·" else STRIP_HEIGHTS[height].toString(), color))
    }
    line = line.append(kit.text("  сейчас", ChatPalette.FAINT))

    val worst = (0 until STRIP_COLUMNS).filter { errors[it] + warnings[it] > 0 }
        .sortedWith(compareByDescending<Int> { errors[it] * 1_000 + warnings[it] }.thenByDescending { it })
        .take(STRIP_WORST)
    val withDate = windowMillis > Duration.ofHours(24).toMillis()
    val tip = mutableListOf<Component>(kit.text("Последние $windowHours ${RussianText.plural(windowHours.toLong(), "час", "часа", "часов")}", ChatPalette.LABEL), kit.blank())
    worst.forEach { column ->
        val start = now - (STRIP_COLUMNS - column) * columnMillis
        var row = kit.text(clock(start, withDate) + "  ", ChatPalette.SOFT)
        if (errors[column] > 0) row = row.append(kit.text("● ${errors[column]}  ", ChatPalette.DANGER))
        if (warnings[column] > 0) row = row.append(kit.text("▲ ${warnings[column]}", ChatPalette.WARN))
        tip += row
    }
    tip += kit.text("Остальное время тихо", ChatPalette.FAINT)
    return line.hoverEvent(kit.tooltip(*tip.toTypedArray()))
}

/** `  ● pnShop ×3 старт  Database is locked`, clickable to open the card; the reason word is dropped when plain. */
private fun notableLine(kit: ChatKit, group: List<JournalRecord>, reason: Reason, now: Long, root: String): Component {
    val record = group.first()
    val color = levelColor(record.level)
    val icon = if (record.level == JournalLevel.ERROR) '●' else '▲'
    val prefix = "  $icon ${record.source}" + (if (group.size > 1) " ×${group.size}" else "") +
        (if (reason != Reason.PLAIN) " ${reason.word}" else "") + "  "
    val budget = (CHAT_WIDTH - prefix.length).coerceAtLeast(MIN_MESSAGE)
    val message = record.message.lineSequence().first().let { if (it.length > budget) it.take(budget - 1) + "…" else it }
    var line = kit.text("  " + kit.glyph(icon) + " ", color).append(kit.text(record.source, ChatPalette.ACCENT))
    if (group.size > 1) line = line.append(kit.text(" ×${group.size}", ChatPalette.ORANGE))
    if (reason != Reason.PLAIN) {
        val reasonColor = when (reason) {
            Reason.STARTUP -> ChatPalette.ORANGE
            Reason.JUST_NOW -> ChatPalette.WARN
            else -> ChatPalette.INFO
        }
        line = line.append(kit.text(" ${reason.word}", reasonColor))
    }
    line = line.append(kit.text("  $message", ChatPalette.SOFT))
    val tip = mutableListOf<Component>(
        kit.text(record.source, ChatPalette.ACCENT),
        kit.text(record.message.lineSequence().first().take(120), ChatPalette.TEXT),
        kit.blank(),
        kit.text(
            if (group.size > 1) "${group.size} раз, первый ${clock(group.last().timeMillis, false)}, последний ${clock(record.timeMillis, false)}"
            else "один раз, в ${clock(record.timeMillis, false)}",
            ChatPalette.SOFT,
        ),
        kit.blank(),
        kit.text("Нажмите, чтобы открыть карточку", ChatPalette.FAINT),
    )
    return line.clickEvent(ClickEvent.runCommand("$root view ${record.fingerprint}")).hoverEvent(kit.tooltip(*tip.toTypedArray()))
}
