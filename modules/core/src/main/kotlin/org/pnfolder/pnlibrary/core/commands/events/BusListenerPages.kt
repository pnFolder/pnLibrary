package org.pnfolder.pnlibrary.core.commands.events

import net.kyori.adventure.text.Component
import org.pnfolder.pnlibrary.api.chat.ChatPalette
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.events.EventInfo
import org.pnfolder.pnlibrary.api.events.EventInspector
import org.pnfolder.pnlibrary.api.events.ListenerInfo
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.common.time.DisplayZone
import org.pnfolder.pnlibrary.core.commands.chatPager
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** Chat pages describing bus listeners. */
internal class BusListenerPages(private val library: PnLibrary) {
    private val bus: EventInspector get() = library.events.inspector

    fun listeners(context: CommandContext, requestedPage: Int) {
        val kit = kit(context)
        val all = bus.listeners()
        val pages = pageCount(all.size)
        val page = requestedPage.coerceIn(1, pages)
        val lines = mutableListOf(
            kit.blank(),
            kit.header("Все слушатели", kit.text("${all.size}", ChatPalette.LABEL)),
            kit.blank(),
        )
        if (all.isEmpty()) lines += kit.note("Слушателей нет.")
        all.drop((page - 1) * PAGE_SIZE).take(PAGE_SIZE).forEach { lines += listenerRow(kit, it) }
        if (pages > 1) {
            lines += kit.blank()
            lines += chatPager(kit, "$ROOT listeners", page, pages)
        }
        lines += kit.blank()
        lines += footer(kit, backToEvents(kit))
        lines.forEach(context.sender::send)
    }

    fun slow(context: CommandContext, requestedPage: Int) {
        val ran = bus.listeners().filter { it.invocations > 0 }.sortedByDescending { it.averageNanos }
        listenerPage(context, requestedPage, ran, "Медленные слушатели", "Слушатели ещё не запускались.", "$ROOT slow")
    }

    fun errors(context: CommandContext, requestedPage: Int) {
        val failed = bus.listeners().filter { it.failures > 0 }.sortedByDescending { it.failures }
        listenerPage(context, requestedPage, failed, "Слушатели с ошибками", "Ошибок не было.", "$ROOT errors")
    }

    fun topCalls(context: CommandContext) = top(context, "Чаще всего вызывались", "Событий ещё не было.") { it.calls }

    fun topDenied(context: CommandContext) = top(context, "Чаще всего отклонялись", "Отказов не было.") { it.denied }

    private fun top(context: CommandContext, title: String, empty: String, count: (EventInfo) -> Long) {
        val kit = kit(context)
        val ranked = bus.events().filter { count(it) > 0 }.sortedByDescending(count).take(TOP_SIZE)
        val busiest = ranked.firstOrNull()?.let(count) ?: 0
        val lines = mutableListOf(kit.blank(), kit.header(title, kit.text("${ranked.size}", ChatPalette.LABEL)), kit.blank())
        if (ranked.isEmpty()) lines += kit.note(empty)
        ranked.forEach {
            lines += kit.text("   ").append(kit.text(bar(kit, count(it), busiest), ChatPalette.SOFT))
                .append(kit.text(" ${count(it)}  ", ChatPalette.LABEL)).append(kit.text(shortName(it.eventType), ChatPalette.TEXT))
        }
        lines += kit.blank()
        lines += footer(kit, backToEvents(kit))
        lines.forEach(context.sender::send)
    }

    fun range(context: CommandContext, word: String) {
        val kit = kit(context)
        val hours = RANGES.getValue(word)
        val totals = callsSince(LocalDateTime.now(DisplayZone.zone).truncatedTo(ChronoUnit.HOURS).minusHours(hours - 1L))
        val ranked = totals.entries.sortedByDescending { it.value.calls }.take(TOP_SIZE)
        val busiest = ranked.firstOrNull()?.value?.calls ?: 0
        val lines = mutableListOf(
            kit.blank(),
            kit.header("Вызовы за $word", kit.text("${totals.values.sumOf { it.calls }}", ChatPalette.LABEL)),
            kit.blank(),
        )
        if (bus.historyDays <= 0) lines += kit.note("История выключена.")
        else if (ranked.isEmpty()) lines += kit.note("За этот период событий не было.")
        ranked.forEach { (name, sums) ->
            var line = kit.text("   ").append(kit.text(bar(kit, sums.calls, busiest), ChatPalette.SOFT))
                .append(kit.text(" ${sums.calls}  ", ChatPalette.LABEL)).append(kit.text(shortName(name), ChatPalette.TEXT))
                .append(kit.text("  ${"%.1f".format(sums.calls.toDouble() / hours)}/ч", ChatPalette.FAINT))
            if (sums.denied > 0) line = line.append(kit.text("  отказов ${sums.denied}", ChatPalette.DANGER))
            lines += line
        }
        lines += kit.blank()
        lines += footer(kit, backToEvents(kit))
        lines.forEach(context.sender::send)
    }

    /** Running call counters of a listener group. */
    private class Totals(var calls: Long = 0, var denied: Long = 0)

    private fun callsSince(since: LocalDateTime): Map<String, Totals> {
        val totals = HashMap<String, Totals>()
        bus.hourly().filter { !it.hour.isBefore(since) }.forEach {
            val sums = totals.getOrPut(it.eventType) { Totals() }
            sums.calls += it.calls
            sums.denied += it.denied
        }
        return totals
    }

    fun exportCsv(context: CommandContext) = export(context, bus.exportHistory(false))

    fun exportJson(context: CommandContext) = export(context, bus.exportHistory(true))

    private fun export(context: CommandContext, file: Path?) {
        context.sender.send(
            if (file == null) Component.text("Выгрузка недоступна: история выключена или файл не записать.", ChatPalette.DANGER)
            else Component.text("Счётчики записаны в $file", ChatPalette.OK),
        )
    }

    fun pluginView(context: CommandContext, id: String, requestedPage: Int) {
        val all = bus.listeners().filter { it.plugin.toString().equals(id, ignoreCase = true) }.sortedByDescending { it.totalNanos }
        listenerPage(context, requestedPage, all, "Плагин $id", "У плагина нет слушателей.", "$ROOT plugin $id")
    }

    private fun listenerPage(context: CommandContext, requestedPage: Int, all: List<ListenerInfo>, title: String, empty: String, base: String) {
        val kit = kit(context)
        val pages = pageCount(all.size)
        val page = requestedPage.coerceIn(1, pages)
        val lines = mutableListOf(kit.blank(), kit.header(title, kit.text("${all.size}", ChatPalette.LABEL)), kit.blank())
        if (all.isEmpty()) lines += kit.note(empty)
        all.drop((page - 1) * PAGE_SIZE).take(PAGE_SIZE).forEach { info ->
            var line = kit.text("   ").append(kit.text(ownerOf(info), ChatPalette.ACCENT)).append(kit.dot())
                .append(kit.text(shortName(info.eventType), ChatPalette.TEXT))
                .append(kit.text("  средн. ${micros(info.averageNanos)}", ChatPalette.LABEL))
                .append(kit.text("  макс. ${micros(info.maxNanos)}", ChatPalette.SOFT))
                .append(kit.text("  запусков ${info.invocations}", ChatPalette.FAINT))
            if (info.failures > 0) line = line.append(kit.text("  ошибок ${info.failures}", ChatPalette.DANGER))
            lines += line
        }
        if (pages > 1) {
            lines += kit.blank()
            lines += chatPager(kit, base, page, pages)
        }
        lines += kit.blank()
        lines += footer(kit, backToEvents(kit))
        lines.forEach(context.sender::send)
    }
}
