package org.pnfolder.pnlibrary.core.menu.screens

import org.pnfolder.pnlibrary.core.menu.ClickType
import org.pnfolder.pnlibrary.core.menu.ItemSpec
import org.pnfolder.pnlibrary.core.menu.MenuRoutes
import org.pnfolder.pnlibrary.core.menu.MenuScreens
import org.pnfolder.pnlibrary.core.menu.MenuSession
import org.pnfolder.pnlibrary.core.menu.MenuViewer
import org.pnfolder.pnlibrary.core.menu.data.SettingRow
import org.pnfolder.pnlibrary.core.menu.style.DATE_FILTER
import org.pnfolder.pnlibrary.core.menu.style.FILTER
import org.pnfolder.pnlibrary.core.menu.style.Icons
import org.pnfolder.pnlibrary.core.menu.style.PAGE_SIZE
import org.pnfolder.pnlibrary.core.menu.style.Row
import org.pnfolder.pnlibrary.core.menu.style.Style
import org.pnfolder.pnlibrary.core.menu.style.TITLE_LIMIT
import org.pnfolder.pnlibrary.core.menu.style.icon
import org.pnfolder.pnlibrary.core.settings.ChoiceSource
import java.time.Instant

/**
 * The screen that chooses one value of a setting that has many (a time zone): one paged list of all values, like the
 * other choice lists, but each value tells about itself, and the list can be sorted, narrowed to the popular values or
 * searched by a part of the name.
 *
 * It knows nothing about the settings screen: the screen hands over what to do on a pick, on typing and on going back.
 */
internal class SettingChoicePicker(session: MenuSession, routes: MenuRoutes) : MenuScreens(session, routes) {
    /** What the picker works on; [pick] writes the chosen value, [type] asks for it in the chat, [back] leaves the picker. */
    class Target(
        val row: SettingRow,
        val source: ChoiceSource,
        val pick: (MenuViewer, String) -> Unit,
        val type: (MenuViewer) -> Unit,
        val back: (MenuViewer) -> Unit,
    )

    /** How the list is shown: the number of the sort order, whether only the popular values, and the text searched for. */
    private data class View(val order: Int = 0, val popular: Boolean = false, val filter: String = "")

    /** Opens the list of values, already narrowed to those that contain [filter] when it is not empty. */
    fun open(player: MenuViewer, target: Target, filter: String = "") = values(player, target, View(filter = filter.trim()), 0)

    private fun values(player: MenuViewer, target: Target, view: View, page: Int) {
        val row = target.row
        val source = target.source
        val now = Instant.now()
        val shown = source.view(view.order, view.popular, view.filter, now)
        val rows = shown.map { value ->
            val chosen = value == row.current
            val lore = Style.lore(emptyList(), source.lore(value, now), *(if (chosen) emptyArray() else arrayOf(Style.left("выбрать"))))
            Row(icon(null, if (chosen) "LIME_DYE" else "PAPER", value, lore, good = chosen), if (chosen) null else go { who -> target.pick(who, value) })
        }
        val empty = if (view.filter.isEmpty()) "Вариантов нет" else "Ничего не найдено"
        list(
            player, title(row, view), rows, page, PAGE_SIZE, { who, to -> values(who, target, view, to) }, target.back, empty,
            extra = { builder ->
                builder.button(DATE_FILTER, typeItem(row, shown.size), go { target.type(it) })
                if (!view.popular) {
                    val next = view.copy(order = (view.order + 1) % source.orders.size)
                    builder.button(FILTER, orderItem(source, view), goClick { who, _ -> values(who, target, next, 0) })
                }
                if (view.filter.isNotEmpty()) {
                    builder.button(TOGGLE, resetItem(view), go { who -> values(who, target, view.copy(filter = ""), 0) })
                } else if (source.popular.isNotEmpty()) {
                    builder.button(TOGGLE, popularItem(view), go { who -> values(who, target, view.copy(popular = !view.popular), 0) })
                }
                builder.button(SEARCH, searchItem(view), goClick { who, how -> search(who, target, view, how) })
            },
        )
    }

    private fun title(row: SettingRow, view: View): String = when {
        view.filter.isNotEmpty() -> "${row.title}: «${view.filter}»"
        view.popular -> "${row.title}: популярные"
        else -> row.title
    }.take(TITLE_LIMIT)

    /** A click asks for the text in the chat; the wheel clears the search. */
    private fun search(player: MenuViewer, target: Target, view: View, how: ClickType) {
        if (how == ClickType.MIDDLE) {
            if (view.filter.isNotEmpty()) values(player, target, view.copy(filter = ""), 0)
            return
        }
        suggestCommand(player, "Напишите часть названия для «${target.row.title}», например new york.", "/pn settings pick ${target.row.key} ")
    }

    private fun orderItem(source: ChoiceSource, view: View): ItemSpec =
        icon(
            null, "HOPPER", "Сортировка",
            Style.lore(emptyList(), emptyList(), Style.left("следующий порядок"), extra = Style.choices(source.orders.map { it.label }, view.order, 0)),
        )

    private fun popularItem(view: View): ItemSpec =
        icon(
            null, "NETHER_STAR", "Популярные",
            Style.lore(emptyList(), emptyList(), Style.left("переключить"), extra = Style.choices(listOf("Все", "Популярные"), if (view.popular) 1 else 0, 0)),
            good = view.popular,
        )

    private fun searchItem(view: View): ItemSpec {
        val hints = listOfNotNull(Style.left("искать по названию"), Style.middle("сбросить поиск").takeIf { view.filter.isNotEmpty() })
        val found = listOfNotNull(view.filter.takeIf { it.isNotEmpty() }?.let { "Ищем" to it })
        return icon(null, "COMPASS", "Поиск", Style.lore(emptyList(), found, *hints.toTypedArray()))
    }

    private fun resetItem(view: View): ItemSpec =
        icon(null, "BARRIER", "Сбросить поиск", Style.lore(emptyList(), listOf("Ищем" to view.filter), Style.left("показать всё")), danger = true)

    private fun typeItem(row: SettingRow, shown: Int): ItemSpec =
        icon(
            Icons.CHANGELOG, "NAME_TAG", "Ввести в чате",
            Style.lore(emptyList(), listOf("Сейчас" to row.current, "Показано" to "$shown"), Style.left("написать значение")),
        )

    private companion object {
        const val TOGGLE = 51
        const val SEARCH = 52
    }
}
