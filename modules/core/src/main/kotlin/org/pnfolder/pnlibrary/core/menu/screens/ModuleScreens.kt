package org.pnfolder.pnlibrary.core.menu.screens

import org.pnfolder.pnlibrary.api.chat.style.ChatTone
import org.pnfolder.pnlibrary.api.plugin.control.ModuleState
import org.pnfolder.pnlibrary.core.commands.modules.DiskReport
import org.pnfolder.pnlibrary.core.commands.modules.DiskRow
import org.pnfolder.pnlibrary.core.commands.modules.DiskUsage
import org.pnfolder.pnlibrary.core.commands.modules.LibraryRow
import org.pnfolder.pnlibrary.core.commands.modules.ModuleLabels
import org.pnfolder.pnlibrary.core.menu.ItemSpec
import org.pnfolder.pnlibrary.core.menu.MenuRoutes
import org.pnfolder.pnlibrary.core.menu.MenuScreens
import org.pnfolder.pnlibrary.core.menu.MenuSession
import org.pnfolder.pnlibrary.core.menu.MenuViewer
import org.pnfolder.pnlibrary.core.menu.Menus
import org.pnfolder.pnlibrary.core.menu.data.ModuleInfo
import org.pnfolder.pnlibrary.core.menu.style.DETAIL_BACK
import org.pnfolder.pnlibrary.core.menu.style.DETAIL_ROWS
import org.pnfolder.pnlibrary.core.menu.style.Icons
import org.pnfolder.pnlibrary.core.menu.style.MenuPermissions
import org.pnfolder.pnlibrary.core.menu.style.PAGE_SIZE
import org.pnfolder.pnlibrary.core.menu.style.Row
import org.pnfolder.pnlibrary.core.menu.style.Style
import org.pnfolder.pnlibrary.core.menu.style.backItem
import org.pnfolder.pnlibrary.core.menu.style.icon
import org.pnfolder.pnlibrary.core.menu.style.menuFrame
import org.pnfolder.pnlibrary.core.menu.style.menuTitle
import org.pnfolder.pnlibrary.core.menu.style.section
import org.pnfolder.pnlibrary.core.text.wrapText

/** Menu screens for the modules of plugins: a list and a card with the same switches as `/pn modules`. */
internal class ModuleScreens(session: MenuSession, routes: MenuRoutes) : MenuScreens(session, routes) {
    /** The modules of every plugin, or only those of [plugin] when it is given. */
    fun modules(player: MenuViewer, plugin: String?, page: Int, back: (MenuViewer) -> Unit = { routes.home(it) }) {
        val all = data.modules().filter { plugin == null || it.key.startsWith("${plugin.lowercase()}:", ignoreCase = true) }
        val rows = all.map { info -> Row(item(info), go { who -> module(who, info.key) { modules(it, plugin, page, back) } }) }
        list(
            player, if (plugin == null) "Модули плагинов" else "Модули: $plugin", rows, page, PAGE_SIZE,
            { who, to -> modules(who, plugin, to, back) }, back, "Модулей нет",
        )
    }

    /**
     * Without [owner] a list of plugins to choose from, each with its libraries counted; with a plugin its libraries, with
     * `plugin:module` the libraries of that module.
     */
    fun libraries(player: MenuViewer, owner: String?, page: Int, back: (MenuViewer) -> Unit) {
        if (owner == null) return pluginsWithLibraries(player, page, back)
        val rows = data.libraries().filter { it.belongsTo(owner) }.map { row -> Row(libraryItem(row, withOwner = owner.contains(':').not()), go { }) }
        list(
            player, "Библиотеки: $owner", rows, page, PAGE_SIZE,
            { who, to -> libraries(who, owner, to, back) }, back, "Библиотек нет",
        )
    }

    /** Every library of every plugin in one list, for when the plugin does not matter. */
    private fun allLibraries(player: MenuViewer, page: Int, back: (MenuViewer) -> Unit) {
        val rows = data.libraries().map { row -> Row(libraryItem(row, withOwner = true), go { }) }
        list(
            player, "Все библиотеки", rows, page, PAGE_SIZE,
            { who, to -> allLibraries(who, to, back) }, back, "Библиотек нет",
        )
    }

    private fun pluginsWithLibraries(player: MenuViewer, page: Int, back: (MenuViewer) -> Unit) {
        val all = data.libraries()
        val reopen = { who: MenuViewer -> libraries(who, null, page, back) }
        val summary = Row(
            icon(
                null, "BOOKSHELF", "Все библиотеки",
                Style.lore(
                    listOf("Одним списком, у каждой написано, чья она."),
                    listOf("Библиотек" to "${all.size}", "Плагинов" to "${all.map { it.plugin }.distinct().size}"),
                    Style.left("открыть"),
                ),
            ),
            go { who -> allLibraries(who, 0, reopen) },
        )
        val plugins = all.groupBy { it.plugin }.toSortedMap(String.CASE_INSENSITIVE_ORDER).map { (plugin, own) ->
            val modules = own.mapNotNull { it.module }.distinct()
            val size = own.mapNotNull { it.file }.distinct().sumOf { java.io.File(it).length() }
            val parameters = buildList {
                add("Библиотек" to "${own.size}")
                if (modules.isNotEmpty()) add("Модулей" to "${modules.size}: ${modules.joinToString().take(40)}")
                if (size > 0) add("Размер" to DiskUsage.format(size))
                if (own.any { it.conflict }) add("Внимание" to "другая версия у другого плагина")
            }
            Row(
                icon(
                    null, if (own.any { it.conflict }) "BARRIER" else "CHEST", plugin,
                    Style.lore(listOf("Что подключили сам плагин и его модули."), parameters, Style.left("открыть")),
                    danger = own.any { it.conflict },
                ),
                go { who -> libraries(who, plugin, 0, reopen) },
            )
        }
        list(
            player, "Библиотеки", listOf(summary) + plugins, page, PAGE_SIZE,
            { who, to -> libraries(who, null, to, back) }, back, "Библиотек нет",
        )
    }

    /** Every plugin by the space it takes, biggest first, with the split into jar, settings, libraries, logs and data. */
    fun disk(player: MenuViewer, page: Int, back: (MenuViewer) -> Unit) {
        away(player) {
            val result = runCatching { data.disk() }
            sync {
                val report = result.getOrNull()
                if (report == null) {
                    tell(player, ChatTone.FAILURE, "Не получилось посчитать диск", result.exceptionOrNull()?.message)
                    return@sync back(player)
                }
                val rows = listOf(Row(diskSummary(report), go { })) + report.rows.map { Row(diskItem(it, report.total), go { }) }
                list(
                    player, "Диск плагинов", rows, page, PAGE_SIZE,
                    { who, to -> disk(who, to, back) }, back, "Плагинов нет",
                )
            }
        }
    }

    private fun diskSummary(report: DiskReport): ItemSpec = icon(
        null, "HOPPER", "Всего",
        Style.lore(
            listOf("Плагины, их папки, настройки и библиотеки."),
            listOf(
                "Занято" to DiskUsage.format(report.total),
                "Библиотек pnLibrary" to DiskUsage.format(report.registeredLibraries),
                "Свободно на диске" to DiskUsage.format(report.free),
                "Размер диска" to DiskUsage.format(report.capacity),
            ),
        ),
    )

    private fun diskItem(row: DiskRow, total: Long): ItemSpec {
        val share = if (total > 0) row.total * 100 / total else 0
        return icon(
            null, "CHEST", row.name,
            Style.lore(
                listOf("Доля: $share% от всех плагинов"),
                listOf(
                    "Всего" to DiskUsage.format(row.total),
                    "Сам плагин" to DiskUsage.format(row.jar),
                    "Настройки" to DiskUsage.format(row.configs),
                    "Библиотеки" to DiskUsage.format(row.libraries),
                    "Журналы" to DiskUsage.format(row.logs),
                    "Данные" to DiskUsage.format(row.data),
                ),
            ),
            danger = share >= 50,
        )
    }

    private fun libraryItem(row: LibraryRow, withOwner: Boolean): ItemSpec {
        val parameters = buildList {
            add("Версия" to row.version)
            add("Принадлежит" to row.ownerText)
            add("Загрузка" to when (row.scope) { "ISOLATED" -> "отдельно"; "LIBRARY" -> "в pnLibrary"; else -> "в плагине" })
            if (row.size >= 0) add("Размер" to DiskUsage.format(row.size))
            row.fileName?.let { add("Файл" to it.takeLastText(40)) }
            row.folder?.let { add("Папка" to it.takeLastText(40)) }
            add("Сумма" to when (row.trust) { LibraryRow.GIVEN -> "задана автором"; else -> "jar не проверяется" })
            if (!row.required) add("Обязательная" to "нет")
            if (row.conflict) add("Внимание" to "другая версия у другого плагина")
            row.repositories.firstOrNull()?.let { add("Откуда" to it.removePrefix("https://").takeLastText(40, fromStart = true)) }
        }
        val description = row.description?.let { wrapText(it, limit = 3) } ?: listOf("Автор не описал эту библиотеку.")
        return icon(
            null, if (row.conflict) "BARRIER" else "BOOK", if (withOwner) "${row.title} · ${row.plugin}" else row.title,
            Style.lore(description, parameters),
            danger = row.conflict, good = !row.conflict && row.trust != LibraryRow.NONE,
        )
    }

    private fun item(info: ModuleInfo): ItemSpec {
        val material = when (info.state) {
            ModuleState.ENABLED -> "LIME_DYE"
            ModuleState.DISABLED -> "GRAY_DYE"
            ModuleState.FAILED -> "BARRIER"
        }
        val parameters = buildList {
            info.description?.let { add("О модуле" to it.take(60)) }
            add("Состояние" to ModuleLabels.label(info.state))
            if (info.controllable) add("С сервером" to if (info.autoStart) "включается" else "не включается")
            else add("Переключатель" to "плагин не дал")
            val libraries = info.libraries.size + info.pluginLibraries.size
            if (libraries > 0) add("Библиотек" to "$libraries")
            info.error?.let { add("Ошибка" to "✖ ${it.take(60)}") }
        }
        return icon(
            null, material, info.title, Style.lore(listOf(info.key), parameters, Style.left("открыть")),
            good = info.state == ModuleState.ENABLED, danger = info.state == ModuleState.FAILED, muted = info.state == ModuleState.DISABLED,
        )
    }

    private fun module(player: MenuViewer, key: String, back: (MenuViewer) -> Unit) {
        val info = data.modules().firstOrNull { it.key == key }
        if (info == null) {
            tell(player, ChatTone.INFO, "Этот модуль уже исчез из списка.")
            return back(player)
        }
        val builder = menuFrame(Menus.chest(menuTitle(info.title)).rows(DETAIL_ROWS), DETAIL_ROWS)
        builder.slot(4, item(info))
        val total = info.libraries.size + info.pluginLibraries.size
        builder.button(
            22,
            section(Icons.CHANGELOG, "BOOKSHELF", "Библиотеки", "Что модуль и его плагин подключили для работы.", "Всего" to "$total"),
            go { who -> libraries(who, key, 0) { module(it, key, back) } },
        )
        if (info.controllable) {
            val reopen = { who: MenuViewer -> module(who, key, back) }
            val on = info.state == ModuleState.ENABLED
            builder.button(10, control(Icons.CHECK, "EMERALD", "Включить", "Запустить модуль.", !on), act(key, "enable", "включён", reopen))
            val off = control(Icons.PROBLEMS, "REDSTONE", "Выключить", "Остановить до следующего запуска сервера.", on)
            builder.button(11, off, act(key, "disable", "выключен", reopen))
            val restart = control(Icons.UPDATES, "REPEATER", "Перезапустить", "Выключить и включить заново.", true)
            builder.button(12, restart, act(key, "restart", "перезапущен", reopen))
            val reload = control(Icons.SETTINGS, "WRITABLE_BOOK", "Перечитать", "Перечитать настройки; если сам не умеет, перезапустится.", on)
            builder.button(13, reload, act(key, "reload", "перечитан", reopen))
            val toggle = if (info.autoStart) "auto-off" to "Не включать с сервером" else "auto-on" to "Включать с сервером"
            builder.button(
                15, control(Icons.HISTORY, "CLOCK", toggle.second, "Сохраняется между перезапусками сервера.", true),
                act(key, toggle.first, "настроен", reopen),
            )
        }
        builder.button(DETAIL_BACK, backItem(), go { back(it) })
        show(player, builder.build())
    }

    private fun control(texture: String?, material: String, label: String, description: String, available: Boolean): ItemSpec =
        icon(
            texture, if (available) material else "COAL", label,
            if (available) Style.lore(listOf(description), emptyList(), Style.left("выполнить")) else Style.lore(listOf(description), emptyList()),
            muted = !available,
        )

    private fun act(key: String, action: String, done: String, reopen: (MenuViewer) -> Unit) = go { who ->
        allowed(who, MenuPermissions.MODULES) {
            away(who) {
                val result = runCatching { data.moduleAction(key, action) }
                sync {
                    result.onSuccess { info ->
                        if (info.error != null) tell(who, ChatTone.FAILURE, "Модуль ${info.title}: ошибка", info.error)
                        else tell(who, ChatTone.SUCCESS, "Модуль ${info.title} $done")
                    }.onFailure { tell(who, ChatTone.FAILURE, "Не получилось", it.message) }
                    reopen(who)
                }
            }
        }
    }
}

/** Cuts a long text to [limit] characters with `…`: the end is kept (the folder), or the start when [fromStart]. */
private fun String.takeLastText(limit: Int, fromStart: Boolean = false): String = when {
    length <= limit -> this
    fromStart -> take(limit - 1) + "…"
    else -> "…" + takeLast(limit - 1)
}
