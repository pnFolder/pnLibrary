package org.pnfolder.pnlibrary.core.commands.events

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import org.pnfolder.pnlibrary.api.chat.ChatKit
import org.pnfolder.pnlibrary.api.chat.ChatPalette
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.events.EventDay
import org.pnfolder.pnlibrary.api.events.EventInspector
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.common.text.RussianText
import org.pnfolder.pnlibrary.common.time.DisplayZone
import org.pnfolder.pnlibrary.core.commands.chatPager

internal class BusHistoryPages(private val library: PnLibrary) {
    private val bus: EventInspector get() = library.events.inspector

    /**
     * One line per day, newest first, so a busy server never floods the chat: the day, its calls, how many different
     * events were called and how many calls were denied. A click opens that day; for one event it opens the event.
     */
    fun history(context: CommandContext, typed: String?, requestedPage: Int) {
        val kit = kit(context)
        val name = typed?.let(bus::resolve)
        val days = bus.history(name).groupBy { it.date }.entries.toList()
        val pages = ((days.size + DAYS_PER_PAGE - 1) / DAYS_PER_PAGE).coerceAtLeast(1)
        val page = requestedPage.coerceIn(1, pages)
        val shown = days.drop((page - 1) * DAYS_PER_PAGE).take(DAYS_PER_PAGE)
        val keep = bus.historyDays
        val details = if (keep <= 0) "выключена" else "хранится $keep ${RussianText.plural(keep.toLong(), "день", "дня", "дней")}"
        val lines = mutableListOf(
            kit.blank(),
            kit.header(if (name == null) "История вызовов" else "История: ${shortName(name)}", kit.text(details, ChatPalette.LABEL)),
            kit.blank(),
        )
        if (keep <= 0) lines += kit.note("История отключена в коде (eventHistoryDays = 0).")
        else if (shown.isEmpty()) lines += kit.note("За это время вызовов не было.")
        val busiest = shown.maxOfOrNull { (_, entries) -> entries.sumOf { it.calls } } ?: 1L
        val today = java.time.LocalDate.now(DisplayZone.zone)
        shown.forEach { (date, entries) -> lines += dayRow(kit, date, today, entries, busiest, name) }
        if (pages > 1) {
            lines += kit.blank()
            lines += chatPager(kit, if (name == null) "$ROOT history" else "$ROOT history of $name", page, pages)
        }
        if (name == null && shown.isNotEmpty()) lines += kit.note("Нажмите на день, чтобы увидеть его события.", ChatPalette.FAINT)
        lines += kit.blank()
        lines += footer(kit, backToEvents(kit))
        lines.forEach(context.sender::send)
    }

    private fun dayRow(
        kit: ChatKit,
        date: java.time.LocalDate,
        today: java.time.LocalDate,
        entries: List<EventDay>,
        busiest: Long,
        name: String?,
    ): Component {
        val total = entries.sumOf { it.calls }
        val denied = entries.sumOf { it.denied }
        val bar = bar(kit, total, busiest)
        var line = kit.text("  ").append(kit.text(bar, ChatPalette.INFO)).append(kit.text(" ${dayTitle(date, today)}", ChatPalette.ACCENT))
            .append(kit.text("  $total ${RussianText.plural(total, "вызов", "вызова", "вызовов")}", ChatPalette.TEXT))
        if (name == null) {
            val kinds = entries.size.toLong()
            line = line.append(kit.text(" · $kinds ${RussianText.plural(kinds, "событие", "события", "событий")}", ChatPalette.SOFT))
        }
        if (denied > 0) line = line.append(kit.text(" · $denied ${RussianText.plural(denied, "отказ", "отказа", "отказов")}", ChatPalette.DANGER))
        if (!kit.reader.interactive) return line
        val top = entries.sortedByDescending { it.calls }.take(HOVER_EVENTS)
        val hover = mutableListOf(kit.text(DAY.format(date), ChatPalette.ACCENT), kit.blank())
        top.forEach { hover += kit.text("${shortName(it.eventType)}: ${it.calls}", ChatPalette.TEXT) }
        if (entries.size > top.size) hover += kit.text("и ещё ${entries.size - top.size}", ChatPalette.FAINT)
        hover += kit.blank()
        hover += kit.text(if (name == null) "Нажмите, чтобы открыть день ›" else "Нажмите, чтобы открыть событие ›", ChatPalette.ACCENT)
        val command = if (name == null) "$ROOT history day $date" else "$ROOT view $name"
        return line.hoverEvent(kit.tooltip(*hover.toTypedArray())).clickEvent(ClickEvent.runCommand(command))
    }

    fun historyDay(context: CommandContext, typed: String, requestedPage: Int) {
        val kit = kit(context)
        val date = runCatching { java.time.LocalDate.parse(typed) }.getOrNull()
        if (date == null) {
            context.sender.send(kit.text("Дата должна быть вида 2026-10-27. Список дней: $ROOT history", ChatPalette.DANGER))
            return
        }
        val entries = bus.history().filter { it.date == date }.sortedByDescending { it.calls }
        val pages = ((entries.size + EVENTS_PER_PAGE - 1) / EVENTS_PER_PAGE).coerceAtLeast(1)
        val page = requestedPage.coerceIn(1, pages)
        val today = java.time.LocalDate.now(DisplayZone.zone)
        val total = entries.sumOf { it.calls }
        val lines = mutableListOf(
            kit.blank(),
            kit.header(dayTitle(date, today), kit.text(DAY.format(date), ChatPalette.LABEL)),
            kit.field("Вызовов", kit.text(total.toString(), ChatPalette.TEXT)),
            kit.field("Разных событий", kit.text(entries.size.toString(), ChatPalette.TEXT)),
            kit.blank(),
        )
        if (entries.isEmpty()) lines += kit.note("В этот день вызовов не было.")
        val busiest = entries.firstOrNull()?.calls ?: 1L
        entries.drop((page - 1) * EVENTS_PER_PAGE).take(EVENTS_PER_PAGE).forEach { lines += historyRow(kit, it, busiest) }
        if (pages > 1) {
            lines += kit.blank()
            lines += chatPager(kit, "$ROOT history day $date", page, pages)
        }
        lines += kit.blank()
        lines += footer(kit, kit.button("назад", "$ROOT history", "К списку дней", ChatPalette.ACCENT))
        lines.forEach(context.sender::send)
    }

    private fun historyRow(kit: ChatKit, day: EventDay, busiest: Long): Component {
        var line = kit.text("    ").append(kit.text(bar(kit, day.calls, busiest), ChatPalette.INFO))
            .append(kit.text(" ${day.calls}", ChatPalette.TEXT)).append(kit.text("  ${shortName(day.eventType)}", ChatPalette.ACCENT))
        if (day.denied > 0) line = line.append(kit.text("  отказов ${day.denied}", ChatPalette.DANGER))
        if (!kit.reader.interactive) return line
        return line.hoverEvent(
            kit.tooltip(
                kit.text(shortName(day.eventType), ChatPalette.ACCENT),
                kit.text(day.eventType, ChatPalette.LABEL),
                kit.blank(),
                kit.hoverField("Вызовов", kit.text(day.calls.toString(), ChatPalette.TEXT)),
                kit.hoverField("Отказов", kit.text(day.denied.toString(), if (day.denied > 0) ChatPalette.DANGER else ChatPalette.TEXT)),
                kit.blank(),
                kit.text("Нажмите, чтобы открыть событие ›", ChatPalette.ACCENT),
            ),
        ).clickEvent(ClickEvent.runCommand("$ROOT view ${day.eventType}"))
    }
}
