package org.pnfolder.pnlibrary.core.journal

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import org.pnfolder.pnlibrary.api.chat.ChatKit
import org.pnfolder.pnlibrary.api.chat.ChatPalette
import org.pnfolder.pnlibrary.api.commands.CommandSender
import org.pnfolder.pnlibrary.common.time.DisplayZone

/** The detail card of one event, or the timeline of its occurrences when [repeats] is set. */
internal fun card(
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
    val (tag, color) = levelStyle(record.level)
    var traceHidden = false
    val lines = mutableListOf<Component>(
        kit.blank(),
        kit.blank(),
        kit.header("Событие $id", kit.bracketed(tag, color)),
        kit.text("  ${record.source}", ChatPalette.ACCENT).append(kit.dot()).append(kit.text(record.type, ChatPalette.LABEL)),
        kit.blank(),
    )
    lines += kit.section("Когда")
    lines += kit.field(
        "Последний раз",
        moment(kit, record.timeMillis).append(kit.text("  ${ago(record.timeMillis)}", agoColor(record.timeMillis))),
    )
    lines += kit.field("Часовой пояс", kit.text(zoneLabel(), ChatPalette.SOFT))
    if (group.size > 1) {
        val gap = (group.first().timeMillis - group.last().timeMillis) / (group.size - 1)
        lines += kit.field("Первый раз", moment(kit, group.last().timeMillis).append(kit.text("  ${ago(group.last().timeMillis)}",
            agoColor(group.last().timeMillis))))
        lines += kit.field(
            "Повторов",
            kit.text(group.size.toString(), ChatPalette.ORANGE).append(kit.text("  примерно раз в ${span(gap)}", ChatPalette.SOFT)),
        )
    }
    lines += kit.blank()
    if (repeats) {
        val zone = DisplayZone.zone
        val today = java.time.LocalDate.now(zone)
        val days = group.take(REPEAT_ROWS).groupBy { java.time.Instant.ofEpochMilli(it.timeMillis).atZone(zone).toLocalDate() }
        days.forEach { (day, items) ->
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
        }
        if (group.size > REPEAT_ROWS) {
            lines += kit.note("Показаны последние $REPEAT_ROWS из ${group.size}.")
            lines += kit.blank()
        }
    } else {
        lines += kit.section("Что случилось")
        wrap(record.message, CARD_WIDTH).forEach { lines += kit.text("    $it", ChatPalette.TEXT) }
        record.details.filterKeys { it != JournalRecord.STACKTRACE }.forEach { (key, value) ->
            lines += kit.text("    $key: ", ChatPalette.LABEL).append(kit.text(value, ChatPalette.SOFT))
        }
        val trace = record.details[JournalRecord.STACKTRACE]?.lines().orEmpty()
        if (trace.isNotEmpty()) {
            lines += kit.blank()
            lines += kit.section("Стек вызовов")
            val (view, hidden) = traceView(kit, trace, sender.isConsole || fullTrace)
            lines += view
            traceHidden = hidden
        }
        lines += kit.blank()
    }
    val buttons = mutableListOf<Component?>(
        kit.button("назад", "$root", "К списку событий", ChatPalette.ACCENT),
        if (repeats) kit.button("карточка", "$root view $id", "Вернуться к карточке", ChatPalette.ACCENT)
        else if (group.size > 1) kit.button("повторы", "$root times $id", "Когда это повторялось", ChatPalette.ACCENT)
        else null,
        if (traceHidden) kit.button("показать всё", "$root full $id", "Стек вызовов целиком, ничего не свёрнуто", ChatPalette.ACCENT)
        else if (fullTrace) kit.button("свернуть", "$root view $id", "Снова свернуть служебные вызовы", ChatPalette.ACCENT)
        else null,
    )
    if (kit.reader.interactive) {
        buttons += kit.bracketed("скопировать", ChatPalette.OK)
            .clickEvent(ClickEvent.copyToClipboard(report(record, group.size)))
            .hoverEvent(
                kit.tooltip(
                    kit.text("Скопировать отчёт для автора плагина", ChatPalette.OK),
                    kit.blank(),
                    kit.text("Время, число повторов, сообщение и весь стек вызовов", ChatPalette.LABEL),
                ),
            )
    }
    lines += kit.actions(*buttons.toTypedArray()) ?: kit.blank()
    lines.forEach(sender::send)
}

/** Plain text for the plugin author: what happened, how often, and the whole stack trace. */
internal fun report(record: JournalRecord, count: Int): String = buildString {
    appendLine("[${record.level}] ${record.source} · ${record.type}")
    appendLine("Время: ${stamp(record.timeMillis)} ${zoneLabel()}, повторов: $count")
    appendLine(record.message)
    record.details.filterKeys { it != JournalRecord.STACKTRACE }.forEach { (key, value) -> appendLine("$key: $value") }
    record.details[JournalRecord.STACKTRACE]?.let {
        appendLine()
        append(it)
    }
}.trim()
