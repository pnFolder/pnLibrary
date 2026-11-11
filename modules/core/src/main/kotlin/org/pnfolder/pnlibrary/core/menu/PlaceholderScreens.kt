package org.pnfolder.pnlibrary.core.menu

internal class PlaceholderScreens(session: MenuSession, routes: MenuRoutes) : MenuScreens(session, routes) {
    /** Every placeholder with its type, format and PlaceholderAPI names; checking a value stays in `/pn placeholders test`. */
    fun placeholders(player: MenuViewer, page: Int) {
        val rows = data.placeholders().map { info ->
            val parameters = buildList {
                add("Тип" to info.type)
                info.format?.let { add("Формат" to it) }
                if (info.published.isNotEmpty()) add("PlaceholderAPI" to info.published.joinToString { "%$it%" })
                if (!info.enabled) add("Состояние" to "выключен")
            }
            Row(
                icon(null, "NAME_TAG", "{${info.address}}", Style.lore(emptyList(), parameters), muted = !info.enabled),
                go { },
            )
        }
        list(player, "Плейсхолдеры", rows, page, PAGE_SIZE, { who, to -> placeholders(who, to) }, { routes.home(it) }, "Плейсхолдеров нет")
    }
}
