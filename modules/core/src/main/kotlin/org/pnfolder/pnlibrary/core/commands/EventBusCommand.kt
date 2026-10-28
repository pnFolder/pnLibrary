package org.pnfolder.pnlibrary.core.commands

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import org.pnfolder.pnlibrary.api.chat.ChatKit
import org.pnfolder.pnlibrary.api.chat.ChatPalette
import org.pnfolder.pnlibrary.api.chat.ChatReader
import org.pnfolder.pnlibrary.api.commands.ArgumentType
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.commands.CommandDefinition
import org.pnfolder.pnlibrary.api.commands.command
import org.pnfolder.pnlibrary.api.events.DispatchMode
import org.pnfolder.pnlibrary.api.events.EventDay
import org.pnfolder.pnlibrary.api.events.EventInfo
import org.pnfolder.pnlibrary.api.events.EventInspector
import org.pnfolder.pnlibrary.api.events.EventTrace
import org.pnfolder.pnlibrary.api.events.ListenerInfo
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.common.text.RussianText
import org.pnfolder.pnlibrary.common.time.DisplayZone

/**
 * `/pn bus`: a window into the event bus, shared by every platform and drawn like the other `/pn` pages.
 *
 * | Command | What it shows |
 * |---|---|
 * | `/pn bus [all\|listened\|called\|denied] [page]` | events with tabs, listener and call counters; click a row to open it |
 * | `/pn bus view <event>` | one event: counters, a recording switch and its listeners in dispatch order |
 * | `/pn bus listeners [page]` | every listener of every plugin |
 * | `/pn bus trace <event>`, `trace off <event>` | switches the recording of an event on or off |
 * | `/pn bus traces [event]` | recorded calls, listener by listener, with the result before and after each |
 * | `/pn bus history [page]` | one line per day: calls, different events, denials; click a day to open it |
 * | `/pn bus history day <date> [page]` | the events of one day, the most called first |
 * | `/pn bus history of <event> [page]` | one line per day for one event |
 * | `/pn bus clear` | forgets the recorded calls |
 *
 * Requires `pnlibrary.events.bus`; the console always may use it. A reader that cannot click gets the same rows
 * and a line with the commands to type.
 */
class EventBusCommand(private val library: PnLibrary) {
    private val bus: EventInspector get() = library.events.inspector

    /** Builds the command tree to mount under `/pn bus`. */
    fun definition(): CommandDefinition = command("bus") {
        permission(PERMISSION)
        noPermissionMessage("Шина событий доступна администраторам. Нужно право $PERMISSION.")
        consoleBypassesPermission()
        executes { list(it, "all", 1) }
        FILTERS.forEach { (word, _) ->
            literal(word) {
                executes { list(it, word, 1) }
                argument("page", ArgumentType.integer()) { executes { list(it, word, it.get<Int>("page")) } }
            }
        }
        literal("view") {
            argument("event", ArgumentType.string()) {
                suggests { eventNames() }
                executes { view(it, it.get<String>("event")) }
            }
        }
        literal("listeners") {
            executes { listeners(it, 1) }
            argument("page", ArgumentType.integer()) { executes { listeners(it, it.get<Int>("page")) } }
        }
        literal("trace") {
            // Without a name the page lists the events, each with its own recording switch, so nothing has to be remembered.
            executes { list(it, "all", 1) }
            literal("off") {
                argument("event", ArgumentType.string()) {
                    suggests { bus.tracedEvents().map(::shortName) }
                    executes { switchTrace(it, it.get<String>("event"), on = false) }
                }
            }
            argument("event", ArgumentType.string()) {
                suggests { eventNames() }
                executes { switchTrace(it, it.get<String>("event"), on = true) }
            }
        }
        literal("traces") {
            executes { traces(it, null) }
            argument("event", ArgumentType.string()) {
                suggests { bus.tracedEvents().map(::shortName) }
                executes { traces(it, it.get<String>("event")) }
            }
        }
        literal("history") {
            executes { history(it, null, 1) }
            argument("page", ArgumentType.integer()) { executes { history(it, null, it.get<Int>("page")) } }
            literal("day") {
                argument("date", ArgumentType.string()) {
                    suggests { bus.history().map { it.date.toString() }.distinct().take(MAX_SUGGESTED_DAYS) }
                    executes { historyDay(it, it.get<String>("date"), 1) }
                    argument("page", ArgumentType.integer()) { executes { historyDay(it, it.get<String>("date"), it.get<Int>("page")) } }
                }
            }
            literal("of") {
                argument("event", ArgumentType.string()) {
                    suggests { eventNames() }
                    executes { history(it, it.get<String>("event"), 1) }
                    argument("page", ArgumentType.integer()) { executes { history(it, it.get<String>("event"), it.get<Int>("page")) } }
                }
            }
        }
        literal("clear") {
            executes {
                bus.clearTraces()
                traces(it, null)
            }
        }
    }

    private fun kit(context: CommandContext) = ChatKit(ChatReader.of(context.sender))

    private fun list(context: CommandContext, word: String, requestedPage: Int) {
        val kit = kit(context)
        val all = bus.events()
        val filter = FILTERS.first { it.first == word }
        val shown = all.filter(filter.second)
        val pages = pageCount(shown.size)
        val page = requestedPage.coerceIn(1, pages)
        val total = bus.listeners().size
        val lines = mutableListOf(
            kit.blank(),
            kit.header("Шина событий", kit.text("$total ${RussianText.plural(total.toLong(), "слушатель", "слушателя", "слушателей")}", ChatPalette.LABEL)),
            tabs(kit, all, word),
            kit.blank(),
        )
        if (shown.isEmpty()) lines += kit.note("Здесь пока пусто.")
        shown.drop((page - 1) * PAGE_SIZE).take(PAGE_SIZE).forEach { lines += row(kit, it) }
        if (pages > 1) {
            lines += kit.blank()
            lines += pager(kit, "$ROOT $word", page, pages)
        }
        lines += kit.blank()
        lines += footer(kit, ROOT, kit.option("все слушатели", "$ROOT listeners", "Каждый слушатель каждого плагина", ChatPalette.ACCENT),
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

    /** One event as a row: a dot that is lit while someone listens, the name, the counters and the recording mark. */
    private fun row(kit: ChatKit, info: EventInfo): Component {
        val listened = info.listeners > 0
        val tracing = info.eventType in bus.tracedEvents()
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
    private fun eventNames(): List<String> {
        val all = bus.events().map { it.eventType }
        return all.map { if (all.count { other -> shortName(other) == shortName(it) } > 1) it else shortName(it) }
    }

    /** The full class name for a typed full or short name; the same text back when nothing matches. */
    private fun resolve(typed: String): String {
        val known = bus.events().map { it.eventType }
        if (typed in known) return typed
        return known.singleOrNull { shortName(it).equals(typed, ignoreCase = true) } ?: typed
    }

    private fun view(context: CommandContext, typed: String) {
        val name = resolve(typed)
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
        listeners.forEach { lines += listenerRow(kit, it, withEvent = false) }
        lines += kit.blank()
        lines += footer(kit, ROOT, kit.button("назад", ROOT, "К списку событий", ChatPalette.ACCENT),
            kit.button("записи", "$ROOT traces $name", "Записанные вызовы этого события", ChatPalette.ACCENT),
            kit.button("история", "$ROOT history of $name", "Вызовы этого события по дням", ChatPalette.ACCENT))
        if (!kit.reader.interactive) lines += kit.note("Запись: $ROOT trace $name | $ROOT trace off $name")
        lines.forEach(context.sender::send)
    }

    /** The recording switch: the pressed choice is checked and colored, the other one runs its command. */
    private fun traceSwitch(kit: ChatKit, name: String, on: Boolean): Component {
        if (!kit.reader.interactive) return kit.text(if (on) "включена" else "выключена", if (on) ChatPalette.OK else ChatPalette.SOFT)
        val enable = if (on) kit.selected("вкл", ChatPalette.OK)
        else kit.option("вкл", "$ROOT trace $name", "Записывать каждый вызов этого события", ChatPalette.SOFT)
        val disable = if (!on) kit.selected("выкл", ChatPalette.SOFT)
        else kit.option("выкл", "$ROOT trace off $name", "Перестать записывать; записанное останется", ChatPalette.SOFT)
        return enable.append(kit.text(" ")).append(disable)
    }

    private fun switchTrace(context: CommandContext, typed: String, on: Boolean) {
        val name = resolve(typed)
        val type = bus.eventType(name)
        if (type == null) {
            context.sender.send(kit(context).text("Такого события нет: $name. Список: $ROOT", ChatPalette.DANGER))
            return
        }
        if (on) bus.enableTracing(type) else bus.disableTracing(type)
        view(context, type.name)
    }

    private fun listeners(context: CommandContext, requestedPage: Int) {
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

    /** A listener as a row: owner, priority and flags, with the source and counters in the hover card. */
    private fun listenerRow(kit: ChatKit, info: ListenerInfo, withEvent: Boolean): Component {
        val owner = info.module?.let { "${info.plugin}/$it" } ?: info.plugin.toString()
        var line = kit.text("   ").append(kit.text(owner, ChatPalette.ACCENT))
        if (withEvent) line = line.append(kit.dot()).append(kit.text(shortName(info.eventType), ChatPalette.TEXT))
        line = line.append(kit.text("  приоритет ${info.priority}", ChatPalette.SOFT))
        if (info.ignoreDenied) line = line.append(kit.text("  ignoreDenied", ChatPalette.INFO))
        if (info.failures > 0) line = line.append(kit.text("  ошибок ${info.failures}", ChatPalette.DANGER))
        if (!kit.reader.interactive) return line.append(kit.text("  ${info.source}", ChatPalette.FAINT))
        return line.hoverEvent(
            kit.tooltip(
                kit.text(owner, ChatPalette.ACCENT),
                kit.blank(),
                kit.hoverField("Событие", kit.text(info.eventType, ChatPalette.TEXT)),
                kit.hoverField("Источник", kit.text(info.source, ChatPalette.TEXT)),
                kit.hoverField("Приоритет", kit.text(info.priority.toString(), ChatPalette.TEXT)),
                kit.hoverField("Запусков", kit.text(info.invocations.toString(), ChatPalette.TEXT)),
                kit.hoverField("Ошибок", kit.text(info.failures.toString(), if (info.failures > 0) ChatPalette.DANGER else ChatPalette.TEXT)),
            ),
        ).clickEvent(ClickEvent.runCommand("$ROOT view ${info.eventType}"))
    }

    private fun traces(context: CommandContext, typed: String?) {
        val name = typed?.let(::resolve)
        val kit = kit(context)
        val all = bus.traces().filter { name == null || it.eventName == name }
        val lines = mutableListOf(
            kit.blank(),
            kit.header("Записанные вызовы", kit.text("${all.size}", ChatPalette.LABEL)),
            kit.blank(),
        )
        if (all.isEmpty()) lines += kit.note("Пусто. Включите запись на странице события: $ROOT view <событие>")
        all.takeLast(MAX_TRACES).reversed().forEach { lines += traceBlock(kit, it) }
        lines += footer(kit, ROOT, kit.button("назад", ROOT, "К списку событий", ChatPalette.ACCENT),
            if (all.isEmpty()) null else kit.button("очистить", "$ROOT clear", "Забыть записанные вызовы", ChatPalette.DANGER))
        lines.forEach(context.sender::send)
    }

    /** `sync` runs on the caller's thread, `async` on a background one where the world must not be touched. */
    private fun modeTag(kit: ChatKit, mode: DispatchMode): Component =
        if (mode == DispatchMode.ASYNC) kit.text("async (мир не трогать)", ChatPalette.WARN) else kit.text("sync", ChatPalette.OK)

    private fun traceBlock(kit: ChatKit, trace: EventTrace): List<Component> {
        val verdict = if (trace.allowed) kit.text("разрешено", ChatPalette.OK) else kit.text("отклонено", ChatPalette.DANGER)
        val lines = mutableListOf(
            kit.text(" ").append(kit.text(shortName(trace.eventName), ChatPalette.ACCENT)).append(kit.dot()).append(verdict)
                .append(kit.text("  результат: ${trace.result ?: "-"}", ChatPalette.SOFT)),
            kit.text("   ").append(modeTag(kit, trace.mode)).append(kit.text("  поток ${trace.thread}", ChatPalette.FAINT)),
        )
        trace.steps.forEach { step ->
            val owner = step.module?.let { "${step.plugin}/$it" } ?: step.plugin.toString()
            val what = when {
                step.skipped -> kit.text("пропущен (ignoreDenied)", ChatPalette.FAINT)
                step.failed -> kit.text("упал", ChatPalette.DANGER)
                step.changedResult -> kit.text("${step.resultBefore} ${kit.glyph('→')} ${step.resultAfter}", ChatPalette.INFO)
                else -> kit.text("результат не менял", ChatPalette.FAINT)
            }
            lines += kit.text("   $owner", ChatPalette.TEXT).append(kit.text("  приоритет ${step.priority}  ", ChatPalette.SOFT)).append(what)
                .append(kit.text("  ${step.nanos / NANOS_PER_MICRO} мкс", ChatPalette.FAINT))
        }
        lines += kit.blank()
        return lines
    }

    /**
     * One line per day, newest first, so a busy server never floods the chat: the day, its calls, how many different
     * events were called and how many calls were denied. A click opens that day; for one event it opens the event.
     */
    private fun history(context: CommandContext, typed: String?, requestedPage: Int) {
        val kit = kit(context)
        val name = typed?.let(::resolve)
        val days = bus.history(name).groupBy { it.date }.entries.toList()
        val pages = ((days.size + DAYS_PER_PAGE - 1) / DAYS_PER_PAGE).coerceAtLeast(1)
        val page = requestedPage.coerceIn(1, pages)
        val shown = days.drop((page - 1) * DAYS_PER_PAGE).take(DAYS_PER_PAGE)
        val keep = bus.historyDays
        val details = if (keep == 0) "выключена" else "хранится $keep ${RussianText.plural(keep.toLong(), "день", "дня", "дней")}"
        val lines = mutableListOf(
            kit.blank(),
            kit.header(if (name == null) "История вызовов" else "История: ${shortName(name)}", kit.text(details, ChatPalette.LABEL)),
            kit.blank(),
        )
        if (keep == 0) lines += kit.note("История отключена в коде (eventHistoryDays = 0).")
        else if (shown.isEmpty()) lines += kit.note("За это время вызовов не было.")
        val busiest = shown.maxOfOrNull { (_, entries) -> entries.sumOf { it.calls } } ?: 1L
        val today = java.time.LocalDate.now(DisplayZone.zone)
        shown.forEach { (date, entries) -> lines += dayRow(kit, date, today, entries, busiest, name) }
        if (pages > 1) {
            lines += kit.blank()
            lines += pager(kit, if (name == null) "$ROOT history" else "$ROOT history of $name", page, pages)
        }
        if (name == null && shown.isNotEmpty()) lines += kit.note("Нажмите на день, чтобы увидеть его события.", ChatPalette.FAINT)
        lines += kit.blank()
        lines += footer(kit, ROOT, kit.button("назад", ROOT, "К списку событий", ChatPalette.ACCENT))
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

    /** The events of one day, the most called first, a few per page. */
    private fun historyDay(context: CommandContext, typed: String, requestedPage: Int) {
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
            lines += pager(kit, "$ROOT history day $date", page, pages)
        }
        lines += kit.blank()
        lines += footer(kit, ROOT, kit.button("назад", "$ROOT history", "К списку дней", ChatPalette.ACCENT))
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

    /** A bar as long as [value] is against [busiest], at least one block. */
    private fun bar(kit: ChatKit, value: Long, busiest: Long): String {
        val width = ((value * BAR_WIDTH + busiest - 1) / busiest.coerceAtLeast(1)).toInt().coerceIn(1, BAR_WIDTH)
        return (if (kit.reader.ascii) "#" else "█").repeat(width)
    }

    private fun dayTitle(date: java.time.LocalDate, today: java.time.LocalDate): String = when (date) {
        today -> "Сегодня"
        today.minusDays(1) -> "Вчера"
        else -> DAY.format(date)
    }

    private fun hints(kit: ChatKit): List<Component> =
        if (kit.reader.interactive) emptyList()
        else kit.commandHints(
            "$ROOT [all|listened|called|denied] [страница]" to "список событий",
            "$ROOT view <событие>" to "карточка события",
            "$ROOT listeners" to "все слушатели",
            "$ROOT trace <событие>" to "записывать вызовы",
            "$ROOT traces" to "записанные вызовы",
            "$ROOT history [страница]" to "вызовы по дням",
            "$ROOT history day <дата> [страница]" to "события одного дня",
            "$ROOT history of <событие>" to "вызовы одного события по дням",
        )

    private fun footer(kit: ChatKit, @Suppress("UNUSED_PARAMETER") root: String, vararg buttons: Component?): List<Component> =
        listOfNotNull(kit.actions(*buttons))

    private fun pager(kit: ChatKit, base: String, page: Int, pages: Int): Component {
        fun link(target: Int, label: String) = kit.option(label, "$base $target", "Страница $target из $pages", ChatPalette.SOFT)
        var line = kit.text("  ")
        line = line.append(if (page > 1) link(page - 1, "‹ назад") else kit.text("[‹ назад]", ChatPalette.FAINT))
        line = line.append(kit.text("  $page / $pages  ", ChatPalette.ACCENT))
        return line.append(if (page < pages) link(page + 1, "дальше ›") else kit.text("[дальше ›]", ChatPalette.FAINT))
    }

    private fun pageCount(total: Int): Int = ((total + PAGE_SIZE - 1) / PAGE_SIZE).coerceAtLeast(1)

    private fun shortName(name: String): String = name.substringAfterLast('.')

    /** Constants of the `/pn bus` command. */
    companion object {
        /** Permission of the whole `/pn bus` tree. */
        const val PERMISSION = "pnlibrary.events.bus"
        private const val ROOT = "/pn bus"
        private const val PAGE_SIZE = 8
        private const val MAX_TRACES = 6
        private const val DAYS_PER_PAGE = 7
        private const val EVENTS_PER_PAGE = 6
        private const val HOVER_EVENTS = 5
        private const val MAX_SUGGESTED_DAYS = 31
        private const val BAR_WIDTH = 12
        private val DAY = java.time.format.DateTimeFormatter.ofPattern("d MMMM", java.util.Locale("ru"))
        private const val NANOS_PER_MICRO = 1_000L

        /** Tab words in order, with what each one keeps. */
        private val FILTERS: List<Pair<String, (EventInfo) -> Boolean>> = listOf(
            "all" to { _: EventInfo -> true },
            "listened" to { e: EventInfo -> e.listeners > 0 },
            "called" to { e: EventInfo -> e.calls > 0 },
            "denied" to { e: EventInfo -> e.denied > 0 },
        )
        private val LABELS = mapOf("all" to "все", "listened" to "со слушателями", "called" to "вызывались", "denied" to "с отказами")
    }
}
