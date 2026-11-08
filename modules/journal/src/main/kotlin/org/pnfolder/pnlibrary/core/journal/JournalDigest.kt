package org.pnfolder.pnlibrary.core.journal

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.TextColor
import org.pnfolder.pnlibrary.api.chat.ChatKit
import org.pnfolder.pnlibrary.api.chat.ChatPalette
import org.pnfolder.pnlibrary.api.chat.ChatReader
import org.pnfolder.pnlibrary.common.text.RussianText
import org.pnfolder.pnlibrary.common.time.DisplayZone
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
fun joinDigest(
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

    val hasErrors = groups.any { it.first().level == JournalLevel.ERROR }
    val ranked = rankByUrgency(groups, now, startedAtMillis)
    val top = ranked.first()
    val topReason = reasonOf(top, now, startedAtMillis)
    val lines = mutableListOf<Component>(
        kit.blank(),
        kit.header("Журнал", kit.text("за последние $windowHours ${RussianText.plural(windowHours.toLong(), "час", "часа", "часов")}", ChatPalette.LABEL)),
        summaryLine(kit, relevant.count { it.level == JournalLevel.ERROR }, relevant.count { it.level == JournalLevel.WARNING }),
        strip(kit, relevant, now, windowHours, startedAtMillis),
    )
    ranked.take(MAX_NOTABLE).forEach { lines += notableLine(kit, it, reasonOf(it, now, startedAtMillis), root) }
    lines += adviceLine(kit, top, topReason, hasErrors)
    lines += kit.blank()
    lines += digestActions(kit, root, hasErrors, topReason != Reason.PLAIN, top)
    return lines
}

/** The buttons under the digest; the card button appears only when something is worth a closer look. */
private fun digestActions(kit: ChatKit, root: String, hasErrors: Boolean, urgentReason: Boolean, top: List<JournalRecord>): Component {
    val urgent = hasErrors || urgentReason
    return kit.actions(
        kit.button("открыть журнал", if (hasErrors) "$root error" else "$root warning", "Список событий", ChatPalette.ACCENT),
        if (urgent) kit.button("подробнее", "$root view ${top.first().fingerprint}", "Карточка главного события", ChatPalette.ACCENT) else null,
    ) ?: kit.blank()
}

/** Most urgent first; errors before warnings inside one reason. If nothing stands out, the newest error (or warning) leads. */
private fun rankByUrgency(groups: List<List<JournalRecord>>, now: Long, startedAtMillis: Long): List<List<JournalRecord>> =
    groups.sortedWith(
        compareBy<List<JournalRecord>> { reasonOf(it, now, startedAtMillis).ordinal }
            .thenBy { if (it.first().level == JournalLevel.ERROR) 0 else 1 },
    )

/** One piece of advice for the top event, or the calm note when nothing is urgent; nothing when only errors stand out plainly. */
private fun adviceLine(kit: ChatKit, top: List<JournalRecord>, topReason: Reason, hasErrors: Boolean): List<Component> {
    if (topReason == Reason.PLAIN) {
        return if (hasErrors) emptyList() else listOf(kit.note("Ничего срочного. Загляните в журнал, когда будет время.", ChatPalette.FAINT))
    }
    // An event that keeps repeating is a repeating problem even when its last repeat was a moment ago.
    val reason = if (topReason == Reason.JUST_NOW && top.size >= RECURRING_FROM) Reason.RECURRING else topReason
    return listOf(kit.note(reason.advice, ChatPalette.FAINT))
}

/** Why [group], newest first, deserves a look: it began at startup, just happened, or keeps coming back. */
private fun reasonOf(group: List<JournalRecord>, now: Long, startedAtMillis: Long): Reason {
    val first = group.last().timeMillis
    return when {
        first >= startedAtMillis - 1_000 && first <= startedAtMillis + STARTUP.toMillis() -> Reason.STARTUP
        group.first().timeMillis >= now - JUST_NOW.toMillis() -> Reason.JUST_NOW
        group.size >= RECURRING_FROM -> Reason.RECURRING
        else -> Reason.PLAIN
    }
}

/** `  ● 3 ошибки   ▲ 2 предупреждения`: only the kinds that occurred. */
private fun summaryLine(kit: ChatKit, errorCount: Int, warningCount: Int): Component {
    val summary = mutableListOf<Component>()
    if (errorCount > 0) {
        summary += kit.text(kit.glyph('●') + " $errorCount ", ChatPalette.DANGER)
            .append(kit.text(RussianText.plural(errorCount.toLong(), "ошибка", "ошибки", "ошибок"), ChatPalette.SOFT))
    }
    if (warningCount > 0) {
        summary += kit.text(kit.glyph('▲') + " $warningCount ", ChatPalette.WARN)
            .append(kit.text(RussianText.plural(warningCount.toLong(), "предупреждение", "предупреждения", "предупреждений"), ChatPalette.SOFT))
    }
    return summary.drop(1).fold(kit.text("  ").append(summary.first())) { line, part -> line.append(kit.text("   ")).append(part) }
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
    val columns = StripColumns(errors, warnings, columnOf(startedAtMillis))
    val withDate = windowMillis > Duration.ofHours(24).toMillis()
    return stripLine(kit, columns).hoverEvent(stripTip(kit, columns, now, columnMillis, windowHours, withDate))
}

/** Problems per column of the strip; [startColumn] is the column of the server start. */
private class StripColumns(val errors: IntArray, val warnings: IntArray, val startColumn: Int) {
    fun total(column: Int) = errors[column] + warnings[column]
}

private fun stripLine(kit: ChatKit, columns: StripColumns): Component {
    var line = kit.text("  ")
    for (column in 0 until STRIP_COLUMNS) {
        val total = columns.total(column)
        val height = when {
            total == 0 -> 0
            total == 1 -> 1
            total <= 3 -> 2
            total <= 7 -> 3
            else -> 4
        }
        val color = when {
            total == 0 -> ChatPalette.FAINT
            column == columns.startColumn -> ChatPalette.ORANGE
            columns.errors[column] > 0 -> ChatPalette.DANGER
            else -> ChatPalette.WARN
        }
        line = line.append(kit.text(if (height == 0) "·" else STRIP_HEIGHTS[height].toString(), color))
    }
    return line.append(kit.text("  сейчас", ChatPalette.FAINT))
}

private fun stripTip(kit: ChatKit, columns: StripColumns, now: Long, columnMillis: Long, windowHours: Int, withDate: Boolean) =
    kit.tooltip(*stripTipLines(kit, columns, now, columnMillis, windowHours, withDate).toTypedArray())

private fun stripTipLines(
    kit: ChatKit,
    columns: StripColumns,
    now: Long,
    columnMillis: Long,
    windowHours: Int,
    withDate: Boolean,
): List<Component> {
    val worst = (0 until STRIP_COLUMNS).filter { columns.total(it) > 0 }
        .sortedWith(compareByDescending<Int> { columns.errors[it] * 1_000 + columns.warnings[it] }.thenByDescending { it })
        .take(STRIP_WORST)
    val tip = mutableListOf<Component>(
        kit.text("Последние $windowHours ${RussianText.plural(windowHours.toLong(), "час", "часа", "часов")}", ChatPalette.LABEL),
        kit.blank(),
    )
    worst.forEach { column ->
        val start = now - (STRIP_COLUMNS - column) * columnMillis
        var row = kit.text(clock(start, withDate) + "  ", ChatPalette.SOFT)
        if (columns.errors[column] > 0) row = row.append(kit.text("● ${columns.errors[column]}  ", ChatPalette.DANGER))
        if (columns.warnings[column] > 0) row = row.append(kit.text("▲ ${columns.warnings[column]}", ChatPalette.WARN))
        tip += row
    }
    tip += kit.text("Остальное время тихо", ChatPalette.FAINT)
    return tip
}

/** `  ● pnShop ×3 старт  Database is locked`, clickable to open the card; the reason word is dropped when plain. */
private fun notableLine(kit: ChatKit, group: List<JournalRecord>, reason: Reason, root: String): Component {
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
    return line.clickEvent(ClickEvent.runCommand("$root view ${record.fingerprint}")).hoverEvent(notableTip(kit, group))
}

private fun notableTip(kit: ChatKit, group: List<JournalRecord>): HoverEvent<Component> {
    val record = group.first()
    return kit.tooltip(
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
}
