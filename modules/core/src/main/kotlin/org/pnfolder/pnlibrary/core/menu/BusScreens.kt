package org.pnfolder.pnlibrary.core.menu

import org.pnfolder.pnlibrary.api.chat.ChatTone
import org.pnfolder.pnlibrary.api.events.EventInfo
import org.pnfolder.pnlibrary.api.events.ListenerInfo
import org.pnfolder.pnlibrary.common.time.DisplayZone
import java.time.LocalDate

internal class BusScreens(session: MenuSession, routes: MenuRoutes) : MenuScreens(session, routes) {
    private enum class BusFilter(val label: String, val keep: (EventInfo) -> Boolean) {
        ALL("все", { true }),
        LISTENED("со слушателями", { it.listeners > 0 }),
        CALLED("вызывались", { it.calls > 0 }),
        DENIED("с отказами", { it.denied > 0 }),
        TODAY("сегодня", { info -> info.lastCalledAt?.atZone(DisplayZone.zone)?.toLocalDate() == LocalDate.now(DisplayZone.zone) }),
    }

    fun busHome(player: MenuViewer) {
        val events = data.events()
        val listeners = data.listeners()
        val today = LocalDate.now(DisplayZone.zone)
        val calls = data.eventHistory(null).filter { it.date == today }.sumOf { it.calls }
        val failures = listeners.sumOf { it.failures }
        val traced = data.tracedEvents().size
        val builder = frame(Menus.chest(title("События плагинов")).rows(HOME_ROWS), HOME_ROWS)
        busSummary(builder, events.size, listeners.size, calls, failures)
        busButtons(builder, events.size, listeners.size, failures, traced)
        builder.button(HOME_CLOSE, backItem(), go { routes.home(it) })
        show(player, builder.build())
    }

    private fun busSummary(builder: MenuBuilder, events: Int, listeners: Int, calls: Any, failures: Long) {
        builder.slot(
            4,
            icon(
                Icons.OVERVIEW, "NETHER_STAR", "События плагинов",
                Style.lore(
                    wrap("Так плагины сообщают друг другу, что произошло. Одни вызывают событие, другие слушают его и отвечают."),
                    listOf(
                        "Событий" to "$events",
                        "Слушателей" to "$listeners",
                        "Вызовов сегодня" to "$calls",
                        "Ошибок" to if (failures > 0) "✖ $failures" else "✔ нет",
                    ),
                ),
            ),
        )
    }

    private fun busButtons(builder: MenuBuilder, events: Int, listeners: Int, failures: Long, traced: Int) {
        builder.button(
            20, section(Icons.UPDATES, "REDSTONE_TORCH", "Все события", "Какие события есть и как часто их вызывают.", "Всего" to "$events"),
            go { busEvents(it, BusFilter.ALL, 0) },
        )
        builder.button(
            22, section(Icons.PLUGINS, "COMPARATOR", "Слушатели", "Кто и на какие события подписан.", "Всего" to "$listeners"),
            go { busListeners(it, ListenerQuery("Слушатели", { who -> busHome(who) }), 0) },
        )
        builder.button(
            24, section(Icons.RELEASES, "BOOKSHELF", "По плагинам", "Слушатели, сгруппированные по плагинам."),
            go { busPlugins(it, 0) },
        )
        builder.button(
            28, section(Icons.CLOCK, "CLOCK", "Медленные", "Слушатели, которые дольше всех работают."),
            go {
                val slow = ListenerQuery("Медленные", { who -> busHome(who) }, { l -> l.invocations > 0 }, compareByDescending { l -> l.averageNanos })
                busListeners(it, slow, 0)
            },
        )
        builder.button(
            30,
            icon(
                Icons.PROBLEMS, "BARRIER", "Ошибки слушателей",
                Style.lore(listOf("Слушатели, которые падали."), listOf("Ошибок" to "$failures"), Style.left("открыть")), danger = failures > 0,
            ),
            go { busListeners(it, ListenerQuery("Ошибки", { who -> busHome(who) }, { l -> l.failures > 0 }, compareByDescending { l -> l.failures }), 0) },
        )
        builder.button(
            32, section(Icons.CHANGELOG, "WRITTEN_BOOK", "Записи вызовов", "Подробный разбор записанных вызовов.", "Записывается" to "$traced"),
            go { routes.busTraces(it, null, 0) },
        )
        builder.button(34, section(Icons.HISTORY, "BOOK", "История по дням", "Сколько раз вызывали события каждый день."), go { routes.busDays(it, null, 0) })
        builder.button(
            38,
            icon(
                Icons.BACKUPS, "CHEST", "Выгрузить в файл",
                Style.lore(listOf("Записать счётчики в CSV рядом с историей."), emptyList(), Style.left("выгрузить")),
            ),
            go { busExport(it) },
        )
    }

    private fun busEvents(player: MenuViewer, filter: BusFilter, page: Int) {
        val next = BusFilter.values()[(filter.ordinal + 1) % BusFilter.values().size]
        val rows = data.events().filter(filter.keep).sortedWith(compareByDescending<EventInfo> { it.lastCalledAt }.thenByDescending { it.calls }).map { info ->
            Row(
                eventItem(info),
                goClick { who, click ->
                    if (click == ClickType.RIGHT) {
                        switchTrace(info.eventType)
                        busEvents(who, filter, page)
                    } else {
                        busEvent(who, info.eventType) { busEvents(it, filter, page) }
                    }
                },
            )
        }
        val cycle = icon(
            Icons.SETTINGS, "HOPPER", "Показать: ${filter.label}",
            Style.lore(
                listOf("Какие события показывать."), emptyList(), Style.left("переключить"),
                extra = Style.choices(BusFilter.values().map { it.label }, filter.ordinal),
            ),
        )
        list(
            player, "События: ${filter.label}", rows, page, PAGE_SIZE, { who, to -> busEvents(who, filter, to) }, { busHome(it) }, "Событий нет",
            extra = { it.button(FILTER, cycle, go { who -> busEvents(who, next, 0) }) },
        )
    }

    private fun eventItem(info: EventInfo): ItemSpec {
        val recording = info.eventType in data.tracedEvents()
        val about = routes.aboutOf(info.eventType)
        val owner = about?.owner
        val purpose = about?.purpose?.takeIf { it.isNotBlank() }?.let { wrap(it, limit = 3) }.orEmpty()
        val parameters = buildList {
            add("Плагин" to (owner?.let { routes.versioned(it) } ?: "сервер или библиотека"))
            add("Слушателей" to "${info.listeners}")
            add("Вызовов" to "${info.calls}")
            info.lastCalledAt?.let { add("Последний" to "${stamp(it)}, ${ago(it)}") }
            if (info.denied > 0) add("Отказов" to "${info.denied}")
            add("Запись" to if (recording) "● идёт" else "не ведётся")
        }
        val material = if (info.listeners > 0) "REDSTONE_TORCH" else "COAL"
        val lore = Style.lore(purpose, parameters, Style.left("открыть событие"), Style.right(if (recording) "остановить запись" else "начать запись"))
        return icon(null, material, shortName(info.eventType), lore, alternative = "REDSTONE_TORCH_ON".takeIf { info.listeners > 0 }, warn = info.denied > 0)
    }

    fun busEvent(player: MenuViewer, name: String, back: (MenuViewer) -> Unit) {
        val info = data.events().firstOrNull { it.eventType == name }
        if (info == null) {
            tell(player, ChatTone.INFO, "Такого события больше нет.")
            return back(player)
        }
        val recording = name in data.tracedEvents()
        val about = routes.aboutOf(name)
        val builder = frame(Menus.chest(title(shortName(name))).rows(DETAIL_ROWS), DETAIL_ROWS)
        val parameters = buildList {
            addAll(routes.ownerParameters(about?.owner))
            add("Слушателей" to "${info.listeners}")
            add("Вызовов" to "${info.calls}")
            add("Отказов" to "${info.denied}")
            add("Последний вызов" to (info.lastCalledAt?.let { "${stamp(it)}, ${ago(it)}" } ?: "с запуска не было"))
            val days = data.eventHistory(name).map { it.date }
            val today = LocalDate.now(DisplayZone.zone)
            if (days.isNotEmpty()) {
                add("Первый день" to dayTitle(days.min(), today))
                add("Последний день" to dayTitle(days.max(), today))
            }
            addAll(routes.labelled("Класс", name))
            about?.usage?.takeIf { it.isNotBlank() }?.let { addAll(routes.labelled("Как использовать", it)) }
        }
        val purpose = about?.purpose?.takeIf { it.isNotBlank() }?.let { wrap(it) } ?: listOf("Автор плагина не описал это событие.")
        builder.slot(4, icon(null, "NETHER_STAR", shortName(name), Style.lore(purpose, parameters)))
        val record = Style.lore(
            listOf("Запоминать каждый вызов: кто ответил и что изменил."),
            listOf("Сейчас" to if (recording) "● запись идёт" else "не ведётся"),
            Style.left(if (recording) "остановить" else "начать"),
        )
        builder.button(
            20,
            icon(Icons.TOGGLE, "LEVER", "Запись вызовов", record, good = recording),
            go { who -> switchTrace(name); busEvent(who, name, back) },
        )
        builder.button(
            22,
            section(Icons.PLUGINS, "COMPARATOR", "Слушатели", "Кто слушает это событие, по порядку вызова.", "Всего" to "${info.listeners}"),
            go { who -> busListeners(who, ListenerQuery("Слушатели ${shortName(name)}", { busEvent(it, name, back) }, { l -> l.eventType == name }), 0) },
        )
        builder.button(24, section(Icons.HISTORY, "BOOK", "История", "Вызовы этого события по дням."), go { routes.busDays(it, name, 0) })
        builder.button(
            30, section(Icons.CHANGELOG, "WRITTEN_BOOK", "Записанные вызовы", "Что случалось при каждом вызове."),
            go { routes.busTraces(it, name, 0) },
        )
        builder.button(DETAIL_BACK, backItem(), go { back(it) })
        show(player, builder.build())
    }

    private fun switchTrace(name: String) {
        data.setTracing(name, name !in data.tracedEvents())
    }

    private class ListenerQuery(
        val title: String,
        val back: (MenuViewer) -> Unit,
        val keep: (ListenerInfo) -> Boolean = { true },
        val order: Comparator<ListenerInfo>? = null,
    )

    private fun busListeners(player: MenuViewer, query: ListenerQuery, page: Int) {
        val all = data.listeners().filter(query.keep).let { if (query.order != null) it.sortedWith(query.order) else it }
        val rows = all.map { info ->
            Row(listenerItem(info), go { who -> busEvent(who, info.eventType) { busListeners(it, query, page) } })
        }
        list(player, query.title, rows, page, PAGE_SIZE, { who, to -> busListeners(who, query, to) }, query.back, "Слушателей нет")
    }

    private fun listenerItem(info: ListenerInfo): ItemSpec {
        val owner = info.module?.let { "${info.plugin}/$it" } ?: info.plugin.toString()
        val parameters = buildList {
            add("Плагин" to routes.ownerLine(info.plugin.toString()))
            add("Событие" to shortName(info.eventType))
            add("Приоритет" to "${info.priority}")
            add("Запусков" to "${info.invocations}")
            add("Среднее" to duration(info.averageNanos))
            add("Максимум" to duration(info.maxNanos))
            if (info.failures > 0) add("Ошибок" to "✖ ${info.failures}")
            if (info.ignoreDenied) add("Особенность" to "работает даже после отказа")
            add("Откуда" to info.source.take(MESSAGE_PREVIEW))
        }
        val lore = Style.lore(emptyList(), parameters, Style.left("открыть событие"))
        return icon(null, "COMPARATOR", owner, lore, alternative = "REDSTONE_COMPARATOR", danger = info.failures > 0)
    }

    private fun busPlugins(player: MenuViewer, page: Int) {
        val rows = data.listeners().groupBy { it.plugin.toString() }.entries.sortedByDescending { it.value.size }.map { (id, group) ->
            val failures = group.sumOf { it.failures }
            val parameters = buildList {
                add("Слушателей" to "${group.size}")
                add("Событий" to "${group.map { it.eventType }.distinct().size}")
                add("Запусков" to "${group.sumOf { it.invocations }}")
                add("Время всего" to duration(group.sumOf { it.totalNanos }))
                if (failures > 0) add("Ошибок" to "✖ $failures")
            }
            val query = ListenerQuery(
                "Плагин $id", { who -> busPlugins(who, page) }, { l -> l.plugin.toString() == id }, compareByDescending { l -> l.totalNanos },
            )
            Row(
                icon(null, "BOOKSHELF", id, Style.lore(emptyList(), parameters, Style.left("слушатели плагина")), danger = failures > 0),
                go { busListeners(it, query, 0) },
            )
        }
        list(player, "События по плагинам", rows, page, PAGE_SIZE, { who, to -> busPlugins(who, to) }, { busHome(it) }, "Слушателей нет")
    }

    private fun busExport(player: MenuViewer) {
        val file = data.exportEventHistory(false)
        if (file == null) tell(player, ChatTone.FAILURE, "Выгрузка недоступна.", "История выключена или файл не записать.")
        else tell(player, ChatTone.SUCCESS, "Счётчики записаны.", file)
    }
}
