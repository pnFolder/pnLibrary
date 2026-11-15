package org.pnfolder.pnlibrary.core.menu.screens

import org.pnfolder.pnlibrary.api.chat.ChatTone
import org.pnfolder.pnlibrary.core.menu.ClickType
import org.pnfolder.pnlibrary.core.menu.ItemSpec
import org.pnfolder.pnlibrary.core.menu.MenuRoutes
import org.pnfolder.pnlibrary.core.menu.MenuScreens
import org.pnfolder.pnlibrary.core.menu.MenuSession
import org.pnfolder.pnlibrary.core.menu.MenuViewer
import org.pnfolder.pnlibrary.core.menu.data.SettingOutcome
import org.pnfolder.pnlibrary.core.menu.data.SettingRow
import org.pnfolder.pnlibrary.core.menu.style.ConfigLook
import org.pnfolder.pnlibrary.core.menu.style.DATE_FILTER
import org.pnfolder.pnlibrary.core.menu.style.Icons
import org.pnfolder.pnlibrary.core.menu.style.MenuPermissions
import org.pnfolder.pnlibrary.core.menu.style.PAGE_SIZE
import org.pnfolder.pnlibrary.core.menu.style.Row
import org.pnfolder.pnlibrary.core.menu.style.Style
import org.pnfolder.pnlibrary.core.menu.style.WRAP_WIDTH
import org.pnfolder.pnlibrary.core.menu.style.icon
import org.pnfolder.pnlibrary.core.menu.style.wrapText

private const val BIG_STEP = 10

/**
 * The page with every setting of the library and its value. A switch flips on a click, a number goes up and down
 * (Shift for ten at a time), a text or a time is typed in the chat, the wheel returns a setting to its default.
 * The same settings are changed by `/pn settings`; the list comes from the server whose menu this is.
 */
internal class LibrarySettingsScreens(session: MenuSession, routes: MenuRoutes) : MenuScreens(session, routes) {
    fun librarySettings(player: MenuViewer, page: Int) {
        val all = data.settings()
        val rows = all.map { Row(item(it), goClick { who, how -> click(who, it, how, page) }) }
        list(
            player, "Параметры библиотеки", rows, page, PAGE_SIZE, { who, to -> librarySettings(who, to) }, { routes.settings(it) },
            "Настроек нет",
            extra = {
                it.slot(
                    DATE_FILTER,
                    ConfigLook.place("Настроек" to "${all.size}", "Изменено" to "${all.count { row -> row.changed }}"),
                )
            },
        )
    }

    private fun item(row: SettingRow): ItemSpec {
        val parameters = buildList {
            if (row.kind != KIND_SWITCH) add("Значение" to row.current)
            if (row.changed) add("По умолчанию" to row.default)
            if (row.range.isNotEmpty()) add("Допустимо" to row.range)
            if (row.examples.isNotEmpty()) add("Примеры" to row.examples.joinToString(", "))
            if (!row.live) add("Заработает" to "после перезапуска")
        }
        val on = row.current == "true"
        val choices = if (row.kind == KIND_SWITCH) Style.choices(listOf("включено", "выключено"), if (on) 0 else 1) else emptyList()
        val (texture, material) = when (row.kind) {
            KIND_SWITCH -> Icons.TOGGLE to if (on) "LIME_DYE" else "GRAY_DYE"
            KIND_NUMBER -> Icons.CLOCK to "REPEATER"
            KIND_TIME -> Icons.CLOCK to "CLOCK"
            else -> Icons.CHANGELOG to "NAME_TAG"
        }
        return icon(
            texture, material, row.title,
            Style.lore(wrapText(row.description, WRAP_WIDTH, 5), parameters, *hints(row), extra = choices),
            good = row.kind == KIND_SWITCH && on, muted = row.kind == KIND_SWITCH && !on, warn = row.changed && row.kind != KIND_SWITCH,
        )
    }

    private fun hints(row: SettingRow): Array<String> = buildList {
        when (row.kind) {
            KIND_SWITCH -> add(Style.left("включить или выключить"))
            KIND_NUMBER -> {
                add(Style.left("больше"))
                add(Style.right("меньше"))
                add(Style.shiftLeft("на десять"))
            }
            else -> add(Style.left("ввести в чате"))
        }
        if (row.changed) add(Style.middle("вернуть значение по умолчанию"))
    }.toTypedArray()

    private fun click(player: MenuViewer, row: SettingRow, how: ClickType, page: Int) {
        if (how == ClickType.MIDDLE) {
            if (row.changed) change(player, row, null, page)
            return
        }
        when (row.kind) {
            KIND_SWITCH -> change(player, row, (row.current != "true").toString(), page)
            KIND_NUMBER -> number(player, row, how, page)
            else -> allowed(player, MenuPermissions.LIBRARY_SETTINGS) {
                suggestCommand(player, "Введите новое значение для «${row.title}». ${hint(row)}".trim(), "/pn settings set ${row.key} ${row.current}")
            }
        }
    }

    private fun hint(row: SettingRow): String = listOfNotNull(
        row.range.takeIf { it.isNotEmpty() }?.let { "Допустимо: $it." },
        row.examples.takeIf { it.isNotEmpty() }?.let { "Например: ${it.joinToString()}." },
    ).joinToString(" ")

    private fun number(player: MenuViewer, row: SettingRow, how: ClickType, page: Int) {
        val current = row.current.toLongOrNull() ?: return
        val step = if (how == ClickType.SHIFT_LEFT || how == ClickType.SHIFT_RIGHT) BIG_STEP else 1
        val direction = if (how == ClickType.RIGHT || how == ClickType.SHIFT_RIGHT) -1 else 1
        change(player, row, (current + direction * step).toString(), page)
    }

    private fun change(player: MenuViewer, row: SettingRow, value: String?, page: Int) {
        allowed(player, MenuPermissions.LIBRARY_SETTINGS) {
            host.async {
                val outcome = try {
                    data.changeSetting(row.key, value, player.actor)
                } catch (error: Exception) {
                    SettingOutcome(false, error.message ?: "Не удалось сохранить настройку")
                }
                host.sync {
                    when {
                        !outcome.ok -> tell(player, ChatTone.FAILURE, "Не изменено.", outcome.message)
                        !outcome.live -> tell(player, ChatTone.WARNING, "Сохранено: ${row.title} = ${outcome.message}.", "Заработает после перезапуска сервера.")
                    }
                    librarySettings(player, page)
                }
            }
        }
    }

    private companion object {
        const val KIND_SWITCH = "SWITCH"
        const val KIND_NUMBER = "NUMBER"
        const val KIND_TIME = "TIME"
    }
}
