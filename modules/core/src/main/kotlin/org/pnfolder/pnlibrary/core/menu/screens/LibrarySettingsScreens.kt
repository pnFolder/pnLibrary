package org.pnfolder.pnlibrary.core.menu.screens

import org.pnfolder.pnlibrary.api.chat.notice.ChatTone
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
import org.pnfolder.pnlibrary.core.menu.style.icon
import org.pnfolder.pnlibrary.core.settings.ChoiceSource
import org.pnfolder.pnlibrary.core.settings.SettingTexts
import org.pnfolder.pnlibrary.core.text.WRAP_WIDTH
import org.pnfolder.pnlibrary.core.text.wrapText

private const val BIG_STEP = 10

/**
 * The page with every setting of the library and its value. A switch flips on a click, a number goes up and down
 * (Shift for ten at a time), a text or a time is typed in the chat, a choice among many values rolls to the next one (Shift opens a picker), the wheel
 * returns a setting to its default.
 * The same settings are changed by `/pn settings`; the list comes from the server whose menu this is.
 */
internal class LibrarySettingsScreens(session: MenuSession, routes: MenuRoutes) : MenuScreens(session, routes) {
    private val picker = SettingChoicePicker(session, routes)

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
        val switch = row.kind == KIND_SWITCH
        val source = ChoiceSource.named(row.choiceSource)
        val options = source?.rolling(row.current).orEmpty()
        val parameters = buildList {
            if (!switch) add("Значение" to row.current)
            if (source != null) add("Вариант" to variant(source, row.current))
            if (source != null) addAll(source.lore(row.current))
            if (row.changed && !switch) add("По умолчанию" to row.default)
            if (row.range.isNotEmpty()) add("Допустимо" to row.range)
            if (row.examples.isNotEmpty() && source == null) add("Примеры" to row.examples.joinToString(", "))
            if (!row.live) add("Заработает" to "после перезапуска")
        }
        val on = row.current == "true"
        val choices = when {
            switch -> Style.choices(
                listOf(SettingTexts.switchWord(true), SettingTexts.switchWord(false)),
                if (on) 0 else 1,
                if (row.default == "true") 0 else 1,
            )
            source != null -> ConfigLook.window(options, options.indexOf(row.current))
            else -> emptyList()
        }
        val (texture, material) = when (row.kind) {
            KIND_SWITCH -> Icons.TOGGLE to if (on) "LIME_DYE" else "GRAY_DYE"
            KIND_NUMBER -> Icons.CLOCK to "REPEATER"
            KIND_TIME -> Icons.CLOCK to "CLOCK"
            KIND_CHOICE -> Icons.SETTINGS to "COMPASS"
            else -> Icons.CHANGELOG to "NAME_TAG"
        }
        return icon(
            texture, material, row.title,
            Style.lore(wrapText(row.description, WRAP_WIDTH, 5), parameters, *hints(row), extra = choices),
            good = switch && on, muted = switch && !on, warn = row.changed && !switch,
        )
    }

    /** `3 из 21`; a value outside the rolled set is not counted in it: `— из 21`. */
    private fun variant(source: ChoiceSource, current: String): String {
        val at = source.cycle.indexOf(current)
        return "${if (at >= 0) at + 1 else "—"} из ${source.cycle.size}"
    }

    private fun hints(row: SettingRow): Array<String> = buildList {
        when (row.kind) {
            KIND_SWITCH -> add(Style.left("включить или выключить"))
            KIND_NUMBER -> {
                add(Style.left("больше"))
                add(Style.right("меньше"))
                add(Style.shiftLeft("на десять"))
            }
            KIND_CHOICE -> if (ChoiceSource.named(row.choiceSource) != null) {
                add(Style.left("следующий"))
                add(Style.right("предыдущий"))
                add(Style.shiftLeft("весь список"))
            } else {
                add(Style.left("ввести в чате"))
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
            KIND_CHOICE -> choose(player, row, how, page)
            else -> type(player, row)
        }
    }

    private fun type(player: MenuViewer, row: SettingRow) = allowed(player, MenuPermissions.LIBRARY_SETTINGS) {
        suggestCommand(player, "Введите новое значение для «${row.title}». ${hint(row)}".trim(), "/pn settings set ${row.key} ${row.current}")
    }

    /**
     * A click rolls to the next value, a right click to the previous one, Shift + click opens the whole list; a setting
     * without a list asks for the value in the chat.
     */
    private fun choose(player: MenuViewer, row: SettingRow, how: ClickType, page: Int) {
        val source = ChoiceSource.named(row.choiceSource) ?: return type(player, row)
        when (how) {
            ClickType.SHIFT_LEFT, ClickType.SHIFT_RIGHT -> pick(player, row, source, "", page)
            ClickType.RIGHT -> change(player, row, source.previous(row.current), page)
            else -> change(player, row, source.next(row.current), page)
        }
    }

    /**
     * Opens the list of values of the choice setting [key] narrowed to [filter], or tells that there is no such setting.
     * `/pn settings pick` opens it this way.
     */
    fun pick(player: MenuViewer, key: String, filter: String) {
        val row = data.settings().firstOrNull { it.key == key }
        val source = row?.let { ChoiceSource.named(it.choiceSource) }
        if (row == null || source == null) return tell(player, ChatTone.FAILURE, "Нет настройки с выбором из списка: $key.")
        pick(player, row, source, filter, 0)
    }

    private fun pick(player: MenuViewer, row: SettingRow, source: ChoiceSource, filter: String, page: Int) = allowed(player, MenuPermissions.LIBRARY_SETTINGS) {
        val target = SettingChoicePicker.Target(row, source, { who, value -> change(who, row, value, page) }, { type(it, row) }, { librarySettings(it, page) })
        picker.open(player, target, filter)
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
                        !outcome.live ->
                            tell(player, ChatTone.WARNING, "Сохранено: ${row.title} = ${outcome.message}.", "Заработает после перезапуска сервера.")
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
        const val KIND_CHOICE = "CHOICE"
    }
}
