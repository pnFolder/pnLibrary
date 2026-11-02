package org.pnfolder.pnlibrary.core.commands

import net.kyori.adventure.text.Component
import org.pnfolder.pnlibrary.api.chat.ChatPalette
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.events.EventInfo
import org.pnfolder.pnlibrary.api.events.EventInspector
import org.pnfolder.pnlibrary.api.events.ListenerInfo
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.common.time.DisplayZone

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
        all.drop((page - 1) * PAGE_SIZE).take(PAGE_SIZE).forEach { lines += listenerRow(kit, it, withEvent = true) }
        if (pages > 1) {
            lines += kit.blank()
            lines += pager(kit, "$ROOT listeners", page, pages)
        }
        lines += kit.blank()
        lines += footer(kit, ROOT, kit.button("назад", ROOT, "К списку событий", ChatPalette.ACCENT))
        lines.forEach(context.sender::send)
    }

    fun slowOrErrors(context: CommandContext, requestedPage: Int, errors: Boolean) {
        val all = if (errors) bus.listeners().filter { it.failures > 0 }.sortedByDescending { it.failures }
        else bus.listeners().filter { it.invocations > 0 }.sortedByDescending { it.averageNanos }
        listenerPage(
            context, requestedPage, all, if (errors) "Слушатели с ошибками" else "Медленные слушатели",
            if (errors) "Ошибок не было." else "Слушатели ещё не запускались.", if (errors) "$ROOT errors" else "$ROOT slow",
        )
    }

    fun top(context: CommandContext, denied: Boolean) {
        val kit = kit(context)
        fun count(info: EventInfo) = if (denied) info.denied else info.calls
        val ranked = bus.events().filter { count(it) > 0 }.sortedByDescending(::count).take(TOP_SIZE)
        val busiest = ranked.firstOrNull()?.let(::count) ?: 0
        val lines = mutableListOf(
            kit.blank(),
            kit.header(if (denied) "Чаще всего отклонялись" else "Чаще всего вызывались", kit.text("${ranked.size}", ChatPalette.LABEL)),
            kit.blank(),
        )
        if (ranked.isEmpty()) lines += kit.note(if (denied) "Отказов не было." else "Событий ещё не было.")
        ranked.forEach {
            lines += kit.text("   ").append(kit.text(bar(kit, count(it), busiest), ChatPalette.SOFT))
                .append(kit.text(" ${count(it)}  ", ChatPalette.LABEL)).append(kit.text(shortName(it.eventType), ChatPalette.TEXT))
        }
        lines += kit.blank()
        lines += footer(kit, ROOT, kit.button("назад", ROOT, "К списку событий", ChatPalette.ACCENT))
        lines.forEach(context.sender::send)
    }

    fun range(context: CommandContext, word: String) {
        val kit = kit(context)
        val hours = RANGES.getValue(word)
        val since = java.time.LocalDateTime.now(DisplayZone.zone).truncatedTo(java.time.temporal.ChronoUnit.HOURS).minusHours(hours - 1L)
        val totals = HashMap<String, LongArray>()
        bus.hourly().filter { !it.hour.isBefore(since) }.forEach {
            val sums = totals.getOrPut(it.eventType) { LongArray(2) }
            sums[0] += it.calls
            sums[1] += it.denied
        }
        val ranked = totals.entries.sortedByDescending { it.value[0] }.take(TOP_SIZE)
        val busiest = ranked.firstOrNull()?.value?.get(0) ?: 0
        val lines = mutableListOf(
            kit.blank(),
            kit.header("Вызовы за $word", kit.text("${totals.values.sumOf { it[0] }}", ChatPalette.LABEL)),
            kit.blank(),
        )
        if (bus.historyDays <= 0) lines += kit.note("История выключена.")
        else if (ranked.isEmpty()) lines += kit.note("За этот период событий не было.")
        ranked.forEach { (name, sums) ->
            var line = kit.text("   ").append(kit.text(bar(kit, sums[0], busiest), ChatPalette.SOFT))
                .append(kit.text(" ${sums[0]}  ", ChatPalette.LABEL)).append(kit.text(shortName(name), ChatPalette.TEXT))
                .append(kit.text("  ${"%.1f".format(sums[0].toDouble() / hours)}/ч", ChatPalette.FAINT))
            if (sums[1] > 0) line = line.append(kit.text("  отказов ${sums[1]}", ChatPalette.DANGER))
            lines += line
        }
        lines += kit.blank()
        lines += footer(kit, ROOT, kit.button("назад", ROOT, "К списку событий", ChatPalette.ACCENT))
        lines.forEach(context.sender::send)
    }

    fun export(context: CommandContext, json: Boolean) {
        val file = bus.exportHistory(json)
        context.sender.send(
            if (file == null) Component.text("Выгрузка недоступна: история выключена или файл не записать.", ChatPalette.DANGER)
            else Component.text("Счётчики записаны в $file", ChatPalette.OK),
        )
    }

    fun pluginView(context: CommandContext, id: String, requestedPage: Int) {
        val all = bus.listeners().filter { it.plugin.toString().equals(id, ignoreCase = true) }.sortedByDescending { it.totalNanos }
        listenerPage(context, requestedPage, all, "Плагин $id", "У плагина нет слушателей.", "$ROOT plugin $id")
    }

    private fun listenerPage(
        context: CommandContext,
        requestedPage: Int,
        all: List<ListenerInfo>,
        title: String,
        empty: String,
        base: String,
    ) {
        val kit = kit(context)
        val pages = pageCount(all.size)
        val page = requestedPage.coerceIn(1, pages)
        val lines = mutableListOf(
            kit.blank(),
            kit.header(title, kit.text("${all.size}", ChatPalette.LABEL)),
            kit.blank(),
        )
        if (all.isEmpty()) lines += kit.note(empty)
        all.drop((page - 1) * PAGE_SIZE).take(PAGE_SIZE).forEach { info ->
            val owner = info.module?.let { "${info.plugin}/$it" } ?: info.plugin.toString()
            var line = kit.text("   ").append(kit.text(owner, ChatPalette.ACCENT)).append(kit.dot())
                .append(kit.text(shortName(info.eventType), ChatPalette.TEXT))
                .append(kit.text("  средн. ${micros(info.averageNanos)}", ChatPalette.LABEL))
                .append(kit.text("  макс. ${micros(info.maxNanos)}", ChatPalette.SOFT))
                .append(kit.text("  запусков ${info.invocations}", ChatPalette.FAINT))
            if (info.failures > 0) line = line.append(kit.text("  ошибок ${info.failures}", ChatPalette.DANGER))
            lines += line
        }
        if (pages > 1) {
            lines += kit.blank()
            lines += pager(kit, base, page, pages)
        }
        lines += kit.blank()
        lines += footer(kit, ROOT, kit.button("назад", ROOT, "К списку событий", ChatPalette.ACCENT))
        lines.forEach(context.sender::send)
    }
}
