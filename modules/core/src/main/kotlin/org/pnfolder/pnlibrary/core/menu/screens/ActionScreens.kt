package org.pnfolder.pnlibrary.core.menu.screens

import org.pnfolder.pnlibrary.core.menu.MenuRoutes
import org.pnfolder.pnlibrary.core.menu.MenuScreens
import org.pnfolder.pnlibrary.core.menu.MenuSession
import org.pnfolder.pnlibrary.core.menu.MenuViewer
import org.pnfolder.pnlibrary.core.menu.style.PAGE_SIZE
import org.pnfolder.pnlibrary.core.menu.style.Row
import org.pnfolder.pnlibrary.core.menu.style.Style
import org.pnfolder.pnlibrary.core.menu.style.icon
import org.pnfolder.pnlibrary.core.scenario.definitionUsage

/** Menu screens for the named actions, requirements and functions that plugins defined. */
internal class ActionScreens(session: MenuSession, routes: MenuRoutes) : MenuScreens(session, routes) {
    /** Every definition with its owner, visibility and the ways to write it; the same as `/pn actions info`. */
    fun actions(player: MenuViewer, page: Int) {
        val rows = data.scenarioDefinitions().map { info ->
            val parameters = buildList {
                add("Вид" to info.kind)
                add("Владелец" to info.owner)
                add("Видимость" to info.visibility)
                definitionUsage(info).forEach { add(it.label to it.text) }
            }
            Row(icon(null, "COMMAND_BLOCK", info.name, Style.lore(emptyList(), parameters)), go { })
        }
        list(player, "Действия", rows, page, PAGE_SIZE, { who, to -> actions(who, to) }, { routes.home(it) }, "Действий нет")
    }
}
