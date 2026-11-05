package org.pnfolder.pnlibrary.core.commands.events

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import org.pnfolder.pnlibrary.api.chat.ChatKit
import org.pnfolder.pnlibrary.api.chat.ChatPalette
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.events.Event
import org.pnfolder.pnlibrary.api.events.EventInfo
import org.pnfolder.pnlibrary.api.events.EventInspector
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.common.text.RussianText
import org.pnfolder.pnlibrary.core.commands.chatPager

internal class BusEventPages(private val library: PnLibrary) {
    private val bus: EventInspector get() = library.events.inspector

    fun list(context: CommandContext, word: String, requestedPage: Int) {
        val kit = kit(context)
        val all = bus.events()
        val filter = FILTERS.first { it.first == word }
        val shown = all.filter(filter.second)
        val traced = bus.tracedEvents()
        val pages = pageCount(shown.size)
        val page = requestedPage.coerceIn(1, pages)
        val total = bus.listeners().size
        val queued = bus.asyncQueued().let { if (it > 0) ", в очереди $it" else "" }
        val count = "$total ${RussianText.plural(total.toLong(), "слушатель", "слушателя", "слушателей")}$queued"
        val lines = mutableListOf(
            kit.blank(),
            kit.header("События плагинов", kit.text(count, ChatPalette.LABEL)),
            tabs(kit, all, word),
            kit.blank(),
        )
        if (shown.isEmpty()) lines += kit.note("Здесь пока пусто.")
        shown.drop((page - 1) * PAGE_SIZE).take(PAGE_SIZE).forEach { lines += row(kit, it, traced) }
        if (pages > 1) {
            lines += kit.blank()
            lines += chatPager(kit, "$ROOT $word", page, pages)
        }
        lines += kit.blank()
        lines += footer(kit, kit.option("все слушатели", "$ROOT listeners", "Каждый слушатель каждого плагина", ChatPalette.ACCENT),
            kit.option("записи", "$ROOT traces", "Записанные вызовы", ChatPalette.ACCENT),
            kit.option("история", "$ROOT history", "Сколько раз вызывали по дням", ChatPalette.ACCENT))
        lines += hints(kit)
        lines.forEach(context.sender::send)
    }

    private fun tabs(kit: ChatKit, all: List<EventInfo>, current: String): Component {
        var line = kit.text(" Показать: ", ChatPalette.LABEL)
        FILTERS.forEachIndexed { index, (word, predicate) ->
            if (index > 0) line = line.append(kit.text(" "))
            val count = all.count(predicate)
            val open = word == current
            val body = if (open) ChatPalette.ACCENT else if (count == 0) ChatPalette.FAINT else ChatPalette.SOFT
            val label = "${LABELS.getValue(word)} $count"
            val button = kit.text("[", ChatPalette.FAINT).append(kit.text(label, body)).append(kit.text("]", ChatPalette.FAINT))
            line = line.append(
                if (open || !kit.reader.interactive) button
                else button.clickEvent(ClickEvent.runCommand("$ROOT $word")).hoverEvent(
                    kit.tooltip(kit.text("Нажмите, чтобы показать: ${LABELS.getValue(word)}", ChatPalette.ACCENT), kit.blank(),
                        kit.text("$ROOT $word", ChatPalette.LABEL)),
                ),
            )
        }
        return line
    }

    private fun row(kit: ChatKit, info: EventInfo, traced: List<String>): Component {
        val listened = info.listeners > 0
        val tracing = info.eventType in traced
        var line = kit.text(" ").append(kit.text(kit.glyph('●'), if (listened) ChatPalette.OK else ChatPalette.FAINT))
            .append(kit.text(" ${shortName(info.eventType)}", ChatPalette.ACCENT))
            .append(kit.text("  ${info.listeners} ${RussianText.plural(info.listeners.toLong(), "слушатель", "слушателя", "слушателей")}", ChatPalette.TEXT))
            .append(kit.text("  ${info.calls} ${RussianText.plural(info.calls, "вызов", "вызова", "вызовов")}", ChatPalette.SOFT))
        if (info.denied > 0) {
            val word = RussianText.plural(info.denied, "отказ", "отказа", "отказов")
            line = line.append(kit.text("  ${info.denied} $word", ChatPalette.DANGER))
        }
        if (!kit.reader.interactive) return if (tracing) line.append(kit.text("  запись", ChatPalette.WARN)) else line
        line = line.append(kit.text("  ")).append(
            if (tracing) kit.option("${kit.glyph('●')} запись", "$ROOT trace off ${info.eventType}", "Остановить запись вызовов", ChatPalette.WARN)
            else kit.option("запись", "$ROOT trace ${info.eventType}", "Записывать каждый вызов этого события", ChatPalette.SOFT),
        )
        val hover = kit.tooltip(
            kit.text(shortName(info.eventType), ChatPalette.ACCENT),
            kit.text(info.eventType, ChatPalette.LABEL),
            kit.blank(),
            kit.hoverField("Слушателей", kit.text(info.listeners.toString(), ChatPalette.TEXT)),
            kit.hoverField("Вызовов", kit.text(info.calls.toString(), ChatPalette.TEXT)),
            kit.hoverField("Отказов", kit.text(info.denied.toString(), if (info.denied > 0) ChatPalette.DANGER else ChatPalette.TEXT)),
            kit.blank(),
            kit.text("Нажмите, чтобы открыть событие ›", ChatPalette.ACCENT),
        )
        return line.hoverEvent(hover).clickEvent(ClickEvent.runCommand("$ROOT view ${info.eventType}"))
    }

    /** Names offered while typing: short ones, and the full name only where two events share a short one. */
    fun eventNames(): List<String> {
        val all = bus.events().map { it.eventType }
        return all.map { if (all.count { other -> shortName(other) == shortName(it) } > 1) it else shortName(it) }
    }

    fun view(context: CommandContext, typed: String) {
        val name = bus.resolve(typed)
        val kit = kit(context)
        val type = bus.eventType(name)
        val info = bus.events().firstOrNull { it.eventType == name }
        if (info == null) {
            context.sender.send(kit.text("Такого события нет: $name. Список: $ROOT", ChatPalette.DANGER))
            return
        }
        val listeners = type?.let { bus.listeners(it) }.orEmpty()
        val tracing = name in bus.tracedEvents()
        val status = if (info.listeners > 0) kit.text("слушают", ChatPalette.OK) else kit.text("никто не слушает", ChatPalette.FAINT)
        val lines = mutableListOf(
            kit.blank(),
            kit.header(shortName(name), status),
            kit.text("  $name", ChatPalette.LABEL),
            kit.blank(),
            kit.field("Слушателей", kit.text(info.listeners.toString(), ChatPalette.TEXT)),
            kit.field("Вызовов", kit.text(info.calls.toString(), ChatPalette.TEXT)),
            kit.field("Отказов", kit.text(info.denied.toString(), if (info.denied > 0) ChatPalette.DANGER else ChatPalette.TEXT)),
            kit.setting("Запись вызовов", traceSwitch(kit, name, tracing)),
            kit.blank(),
            kit.section("Слушатели"),
        )
        if (listeners.isEmpty()) lines += kit.note("   Никто не слушает это событие.")
        listeners.forEach { lines += eventListenerRow(kit, it) }
        lines += kit.blank()
        lines += footer(kit, backToEvents(kit),
            kit.button("записи", "$ROOT traces $name", "Записанные вызовы этого события", ChatPalette.ACCENT),
            kit.button("история", "$ROOT history of $name", "Вызовы этого события по дням", ChatPalette.ACCENT))
        if (!kit.reader.interactive) lines += kit.note("Запись: $ROOT trace $name | $ROOT trace off $name")
        lines.forEach(context.sender::send)
    }

    private fun traceSwitch(kit: ChatKit, name: String, on: Boolean): Component {
        if (!kit.reader.interactive) return kit.text(if (on) "включена" else "выключена", if (on) ChatPalette.OK else ChatPalette.SOFT)
        val enable = if (on) kit.selected("вкл", ChatPalette.OK)
        else kit.option("вкл", "$ROOT trace $name", "Записывать каждый вызов этого события", ChatPalette.SOFT)
        val disable = if (!on) kit.selected("выкл", ChatPalette.SOFT)
        else kit.option("выкл", "$ROOT trace off $name", "Перестать записывать; записанное останется", ChatPalette.SOFT)
        return enable.append(kit.text(" ")).append(disable)
    }

    fun startTrace(context: CommandContext, typed: String) = withEventType(context, typed) { bus.enableTracing(it) }

    fun stopTrace(context: CommandContext, typed: String) = withEventType(context, typed) { bus.disableTracing(it) }

    private fun withEventType(context: CommandContext, typed: String, change: (Class<out Event>) -> Unit) {
        val name = bus.resolve(typed)
        val type = bus.eventType(name)
        if (type == null) {
            context.sender.send(kit(context).text("Такого события нет: $name. Список: $ROOT", ChatPalette.DANGER))
            return
        }
        change(type)
        view(context, type.name)
    }
}
