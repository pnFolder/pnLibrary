package org.pnfolder.pnlibrary.core.menu

import org.pnfolder.pnlibrary.api.events.DispatchMode
import org.pnfolder.pnlibrary.api.events.EventTrace
import org.pnfolder.pnlibrary.common.time.DisplayZone
import java.time.LocalDate

/** Menu screens listing recent event bus history. */
internal class BusHistoryScreens(session: MenuSession, routes: MenuRoutes) : MenuScreens(session, routes) {
    fun busDays(player: MenuViewer, event: String?, page: Int) {
        val back: (MenuViewer) -> Unit = if (event == null) { who -> routes.busHome(who) } else { who -> routes.busEvent(who, event) { routes.busHome(it) } }
        val today = LocalDate.now(DisplayZone.zone)
        val days = data.eventHistory(event).groupBy { it.date }.entries.sortedByDescending { it.key }
        val busiest = days.maxOfOrNull { (_, entries) -> entries.sumOf { it.calls } } ?: 1L
        val rows = days.map { (date, entries) ->
            val total = entries.sumOf { it.calls }
            val denied = entries.sumOf { it.denied }
            val parameters = buildList {
                add("Вызовов" to "$total")
                if (event == null) add("Событий" to "${entries.size}")
                if (denied > 0) add("Отказов" to "$denied")
                add("Нагрузка" to bar(total, busiest))
            }
            val hint = if (event == null) Style.left("события этого дня") else Style.left("открыть событие")
            val click = if (event == null) go { who -> busDay(who, date, 0, page) } else go { who -> routes.busEvent(who, event) { busDays(it, event, page) } }
            Row(icon(null, "PAPER", dayTitle(date, today), Style.lore(emptyList(), parameters, hint)), click)
        }
        val title = if (event == null) "История событий" else "История ${shortName(event)}"
        val empty = if (data.eventHistoryDays() <= 0) "История выключена в коде" else "Вызовов за это время не было"
        list(player, title, rows, page, PAGE_SIZE, { who, to -> busDays(who, event, to) }, back, empty)
    }

    private fun busDay(player: MenuViewer, date: LocalDate, page: Int, daysPage: Int) {
        val entries = data.eventHistory(null).filter { it.date == date }.sortedByDescending { it.calls }
        val busiest = entries.firstOrNull()?.calls ?: 1L
        val rows = entries.map { day ->
            val parameters = buildList {
                add("Вызовов" to "${day.calls}")
                if (day.denied > 0) add("Отказов" to "${day.denied}")
                add("Нагрузка" to bar(day.calls, busiest))
            }
            Row(
                icon(null, "PAPER", shortName(day.eventType), Style.lore(emptyList(), parameters, Style.left("открыть событие")), warn = day.denied > 0),
                go { who -> routes.busEvent(who, day.eventType) { busDay(it, date, page, daysPage) } },
            )
        }
        val today = LocalDate.now(DisplayZone.zone)
        list(
            player, dayTitle(date, today), rows, page, PAGE_SIZE, { who, to -> busDay(who, date, to, daysPage) },
            { busDays(it, null, daysPage) }, "В этот день вызовов не было",
        )
    }

    fun busTraces(player: MenuViewer, event: String?, page: Int) {
        val all = data.traces().filter { event == null || it.eventName == event }.reversed()
        val rows = all.map { trace -> Row(traceItem(trace), go { who -> busTrace(who, trace, 0) { busTraces(it, event, page) } }) }
        val back: (MenuViewer) -> Unit = if (event == null) { who -> routes.busHome(who) } else { who -> routes.busEvent(who, event) { routes.busHome(it) } }
        val clear = icon(
            Icons.RESET, "TNT", "Очистить записи",
            Style.lore(listOf("Забыть все записанные вызовы."), emptyList(), Style.left("очистить")), danger = true,
        )
        list(
            player, "Записанные вызовы", rows, page, PAGE_SIZE, { who, to -> busTraces(who, event, to) }, back,
            "Пока ничего не записано. Включите запись на странице события.",
            extra = { if (all.isNotEmpty()) it.button(FILTER, clear, go { who -> data.clearTraces(); busTraces(who, event, 0) }) },
        )
    }

    private fun traceItem(trace: EventTrace): ItemSpec {
        val steps = trace.steps.take(TRACE_STEPS).map { step ->
            val owner = step.module?.let { "${step.plugin}/$it" } ?: step.plugin.toString()
            val action = when {
                step.skipped -> "пропущен"
                step.failed -> "упал"
                step.changedResult -> "${step.resultBefore} → ${step.resultAfter}"
                else -> "без изменений"
            }
            "" to "$owner · $action"
        }
        val parameters = buildList {
            trace.at?.let { add("Когда" to stamp(it)); add("" to ago(it)) }
            add("Результат" to (trace.result ?: "-"))
            add("Режим" to if (trace.mode == DispatchMode.ASYNC) "async, мир не трогать" else "sync")
            add("Поток" to trace.thread)
            add("Ответили" to "${trace.steps.size}")
            addAll(steps)
        }
        val verdict = if (trace.allowed) "разрешено" else "отклонено"
        val lore = Style.lore(emptyList(), parameters, Style.left("все ответы"))
        return icon(null, "PAPER", "${shortName(trace.eventName)}: $verdict", lore, danger = !trace.allowed)
    }

    private fun busTrace(player: MenuViewer, trace: EventTrace, page: Int, back: (MenuViewer) -> Unit) {
        val rows = trace.steps.mapIndexed { index, step ->
            val owner = step.module?.let { "${step.plugin}/$it" } ?: step.plugin.toString()
            val action = when {
                step.skipped -> "пропущен"
                step.failed -> "упал"
                step.changedResult -> "изменил результат"
                else -> "без изменений"
            }
            val parameters = buildList {
                add("Плагин" to routes.ownerLine(step.plugin.toString()))
                add("Порядок" to "${index + 1} из ${trace.steps.size}")
                add("Приоритет" to "${step.priority}")
                add("Что сделал" to action)
                if (step.changedResult) add("Результат" to "${step.resultBefore} → ${step.resultAfter}")
                if (!step.skipped) add("Время" to duration(step.nanos))
            }
            Row(icon(null, "PAPER", owner, Style.lore(emptyList(), parameters), danger = step.failed, warn = step.changedResult, muted = step.skipped))
        }
        val title = "${shortName(trace.eventName)}${trace.at?.let { ", ${stamp(it)}" }.orEmpty()}"
        list(player, title.take(TITLE_LIMIT), rows, page, PAGE_SIZE, { who, to -> busTrace(who, trace, to, back) }, back, "Никто не отвечал")
    }
}
