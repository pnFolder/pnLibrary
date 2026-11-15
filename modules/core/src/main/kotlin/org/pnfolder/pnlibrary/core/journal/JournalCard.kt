package org.pnfolder.pnlibrary.core.journal

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import org.pnfolder.pnlibrary.api.chat.ChatKit
import org.pnfolder.pnlibrary.api.chat.ChatPalette
import org.pnfolder.pnlibrary.api.commands.CommandSender
import org.pnfolder.pnlibrary.common.time.DisplayZone

/** The detail card of one event, or the timeline of its occurrences when [repeats] is set. */
fun card(
    sender: CommandSender,
    kit: ChatKit,
    journal: JournalStore,
    id: String?,
    repeats: Boolean,
    fullTrace: Boolean = false,
    root: String,
) {
    val group = journal.read(limit = SCAN_LIMIT).filter { it.fingerprint == id }.sortedByDescending { it.timeMillis }
    if (id == null || group.isEmpty()) {
        sender.send(kit.text("Такой записи нет: возможно, её уже удалили по сроку хранения.", ChatPalette.DANGER))
        return
    }
    val record = group.first()
    val lines = cardHead(kit, id, record).toMutableList()
    lines += whenSection(kit, group)
    lines += kit.blank()
    val (body, traceHidden) =
        if (repeats) repeatsSection(kit, group) to false else detailsSection(kit, record, sender.isConsole || fullTrace)
    lines += body
    val buttons = cardButtons(kit, root, id, repeats, fullTrace, traceHidden, group)
    lines += kit.actions(*buttons.toTypedArray()) ?: kit.blank()
    lines.forEach(sender::send)
}

/** The title of the card: the event id and level, its source and type. */
private fun cardHead(kit: ChatKit, id: String, record: JournalRecord): List<Component> {
    val (tag, color) = levelStyle(record.level)
    return listOf(
        kit.blank(),
        kit.blank(),
        kit.header("Событие $id", kit.bracketed(tag, color)),
        kit.text("  ${record.source}", ChatPalette.ACCENT).append(kit.dot()).append(kit.text(record.type, ChatPalette.LABEL)),
        kit.blank(),
    )
}

/** The first and the last time of the event and how often it repeated. */
private fun whenSection(kit: ChatKit, group: List<JournalRecord>): List<Component> {
    val last = group.first().timeMillis
    val first = group.last().timeMillis
    val lines = mutableListOf(
        kit.section("Когда"),
        kit.field("Последний раз", moment(kit, last).append(kit.text("  ${ago(last)}", agoColor(last)))),
        kit.field("Часовой пояс", kit.text(zoneLabel(), ChatPalette.SOFT)),
    )
    if (group.size > 1) {
        val gap = (last - first) / (group.size - 1)
        lines += kit.field("Первый раз", moment(kit, first).append(kit.text("  ${ago(first)}", agoColor(first))))
        lines += kit.field(
            "Повторов",
            kit.text(group.size.toString(), ChatPalette.ORANGE).append(kit.text("  примерно раз в ${span(gap)}", ChatPalette.SOFT)),
        )
    }
    return lines
}

/** The occurrences of the event, newest first, grouped by day. */
private fun repeatsSection(kit: ChatKit, group: List<JournalRecord>): List<Component> {
    val lines = mutableListOf<Component>()
    val zone = DisplayZone.zone
    val today = java.time.LocalDate.now(zone)
    val days = group.take(REPEAT_ROWS).groupBy { java.time.Instant.ofEpochMilli(it.timeMillis).atZone(zone).toLocalDate() }
    days.forEach { (day, items) -> lines += dayRows(kit, day, items, today, zone) }
    if (group.size > REPEAT_ROWS) {
        lines += kit.note("Показаны последние $REPEAT_ROWS из ${group.size}.")
        lines += kit.blank()
    }
    return lines
}

/** The heading of one day and a row for each occurrence of it. */
private fun dayRows(
    kit: ChatKit,
    day: java.time.LocalDate,
    items: List<JournalRecord>,
    today: java.time.LocalDate,
    zone: java.time.ZoneId,
): List<Component> {
    val lines = mutableListOf<Component>()
    val title = when (day) {
        today -> "Сегодня"
        today.minusDays(1) -> "Вчера"
        else -> longDate(day)
    }
    val dayTip = kit.tooltip(
        kit.text(DAY_WITH_YEAR.format(day), ChatPalette.TEXT),
        kit.text(WEEKDAY.format(day), ChatPalette.LABEL),
        kit.blank(),
        kit.hoverField("Событий в этот день", kit.text(items.size.toString(), ChatPalette.ORANGE)),
    )
    var heading = kit.section(title)
    if (day == today || day == today.minusDays(1)) heading = heading.append(kit.text(" · ${longDate(day)}", ChatPalette.SOFT))
    lines += heading.append(kit.text("  ×${items.size}", ChatPalette.ORANGE)).hoverEvent(dayTip)
    items.forEach { item ->
        lines += kit.text("    " + CLOCK.withZone(zone).format(java.time.Instant.ofEpochMilli(item.timeMillis)), ChatPalette.TEXT)
            .append(kit.text("  ${ago(item.timeMillis)}", agoColor(item.timeMillis)))
            .hoverEvent(momentTip(kit, item.timeMillis))
    }
    lines += kit.blank()
    return lines
}

/** The message, its details and the stack trace; the flag says whether some trace lines were folded away. */
private fun detailsSection(kit: ChatKit, record: JournalRecord, wholeTrace: Boolean): Pair<List<Component>, Boolean> {
    val lines = mutableListOf<Component>(kit.section("Что случилось"))
    wrap(record.message, CARD_WIDTH).forEach { lines += kit.text("    $it", ChatPalette.TEXT) }
    record.details.filterKeys { it != JournalRecord.STACKTRACE }.forEach { (key, value) ->
        lines += kit.text("    $key: ", ChatPalette.LABEL).append(kit.text(value, ChatPalette.SOFT))
    }
    var hidden = false
    val trace = record.details[JournalRecord.STACKTRACE]?.lines().orEmpty()
    if (trace.isNotEmpty()) {
        lines += kit.blank()
        lines += kit.section("Стек вызовов")
        val (view, folded) = traceView(kit, trace, wholeTrace)
        lines += view
        hidden = folded
    }
    lines += kit.blank()
    return lines to hidden
}

/** The buttons under the card; a null entry is a button that does not apply. */
private fun cardButtons(
    kit: ChatKit,
    root: String,
    id: String,
    repeats: Boolean,
    fullTrace: Boolean,
    traceHidden: Boolean,
    group: List<JournalRecord>,
): List<Component?> {
    val buttons = mutableListOf<Component?>(
        kit.button("назад", root, "К списку событий", ChatPalette.ACCENT),
        if (repeats) kit.button("карточка", "$root view $id", "Вернуться к карточке", ChatPalette.ACCENT)
        else if (group.size > 1) kit.button("повторы", "$root times $id", "Когда это повторялось", ChatPalette.ACCENT)
        else null,
        if (traceHidden) kit.button("показать всё", "$root full $id", "Стек вызовов целиком, ничего не свёрнуто", ChatPalette.ACCENT)
        else if (fullTrace) kit.button("свернуть", "$root view $id", "Снова свернуть служебные вызовы", ChatPalette.ACCENT)
        else null,
    )
    if (kit.reader.interactive) buttons += copyButton(kit, group)
    return buttons
}

/** Copies the report for the plugin author; only in a chat that can click. */
private fun copyButton(kit: ChatKit, group: List<JournalRecord>): Component =
    kit.bracketed("скопировать", ChatPalette.OK)
        .clickEvent(ClickEvent.copyToClipboard(report(group.first(), group.size)))
        .hoverEvent(
            kit.tooltip(
                kit.text("Скопировать отчёт для автора плагина", ChatPalette.OK),
                kit.blank(),
                kit.text("Время, число повторов, сообщение и весь стек вызовов", ChatPalette.LABEL),
            ),
        )

/** Plain text for the plugin author: what happened, how often, and the whole stack trace. */
fun report(record: JournalRecord, count: Int): String = buildString {
    appendLine("[${record.level}] ${record.source} · ${record.type}")
    appendLine("Время: ${stamp(record.timeMillis)} ${zoneLabel()}, повторов: $count")
    appendLine(record.message)
    record.details.filterKeys { it != JournalRecord.STACKTRACE }.forEach { (key, value) -> appendLine("$key: $value") }
    record.details[JournalRecord.STACKTRACE]?.let {
        appendLine()
        append(it)
    }
}.trim()
