package org.pnfolder.pnlibrary.core.menu.screens

import org.pnfolder.pnlibrary.core.menu.MenuRoutes
import org.pnfolder.pnlibrary.core.menu.MenuScreens
import org.pnfolder.pnlibrary.core.menu.MenuSession
import org.pnfolder.pnlibrary.core.menu.MenuViewer
import org.pnfolder.pnlibrary.core.menu.style.PAGE_SIZE
import org.pnfolder.pnlibrary.core.menu.style.Row
import org.pnfolder.pnlibrary.core.menu.style.Style
import org.pnfolder.pnlibrary.core.menu.style.icon
import org.pnfolder.pnlibrary.core.placeholders.usageVariants

/** Menu screens for registered placeholders. */
internal class PlaceholderScreens(session: MenuSession, routes: MenuRoutes) : MenuScreens(session, routes) {
    /** Every placeholder with its type, format and PlaceholderAPI names; checking a value stays in `/pn placeholders test`. */
    fun placeholders(player: MenuViewer, page: Int) {
        val rows = data.placeholders().map { info ->
            val parameters = buildList {
                add("Тип" to info.type)
                if (info.description.isNotEmpty()) add("Описание" to info.description)
                add("Владелец" to info.owner)
                add("Видимость" to info.visibility.name.lowercase())
                info.format?.let { add("Формат" to it) }
                if (info.published.isNotEmpty()) add("PlaceholderAPI" to info.published.joinToString { "%$it%" })
                usageVariants(info).forEach { add(it.label to it.text) }
            }
            Row(
                icon(null, "NAME_TAG", "{${info.address}}", Style.lore(emptyList(), parameters)),
                go { },
            )
        }
        list(player, "Плейсхолдеры", rows, page, PAGE_SIZE, { who, to -> placeholders(who, to) }, { routes.home(it) }, "Плейсхолдеров нет")
    }
}
