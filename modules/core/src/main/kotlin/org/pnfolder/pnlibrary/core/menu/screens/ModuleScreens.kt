package org.pnfolder.pnlibrary.core.menu.screens

import org.pnfolder.pnlibrary.api.chat.style.ChatTone
import org.pnfolder.pnlibrary.api.plugin.control.ModuleState
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

    private fun item(info: ModuleInfo): ItemSpec {
        val material = when (info.state) {
            ModuleState.ENABLED -> "LIME_DYE"
            ModuleState.DISABLED -> "GRAY_DYE"
            ModuleState.FAILED -> "BARRIER"
        }
        val parameters = buildList {
            add("Состояние" to ModuleLabels.label(info.state))
            if (info.controllable) add("С сервером" to if (info.autoStart) "включается" else "не включается")
            else add("Переключатель" to "плагин не дал")
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
        if (info.controllable) {
            val reopen = { who: MenuViewer -> module(who, key, back) }
            val on = info.state == ModuleState.ENABLED
            builder.button(10, control(Icons.CHECK, "EMERALD", "Включить", "Запустить модуль.", !on), act(key, "enable", "включён", reopen))
            builder.button(11, control(Icons.PROBLEMS, "REDSTONE", "Выключить", "Остановить до следующего запуска сервера.", on), act(key, "disable", "выключен", reopen))
            builder.button(12, control(Icons.UPDATES, "REPEATER", "Перезапустить", "Выключить и включить заново.", true), act(key, "restart", "перезапущен", reopen))
            builder.button(13, control(Icons.SETTINGS, "WRITABLE_BOOK", "Перечитать", "Перечитать настройки; если сам не умеет, перезапустится.", on), act(key, "reload", "перечитан", reopen))
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
