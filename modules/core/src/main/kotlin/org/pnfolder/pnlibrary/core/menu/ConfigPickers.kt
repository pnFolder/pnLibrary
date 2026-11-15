package org.pnfolder.pnlibrary.core.menu

import org.pnfolder.pnlibrary.api.config.ConfigSetting
import org.pnfolder.pnlibrary.api.config.ConfigSettingKind

/** Writes [text] into a setting, then runs [after] with the viewer; the screens that edit a value share it. */
internal typealias SettingWriter = (MenuViewer, ConfigSetting, String, (MenuViewer) -> Unit) -> Unit

/**
 * The two screens that edit a setting more comfortably than a click on its item: a number editor with steps of
 * different size, and a paged list of choices for a setting that has many.
 */
internal class ConfigPickers(session: MenuSession, routes: MenuRoutes) : MenuScreens(session, routes) {
    private fun setting(id: String, path: String): ConfigSetting? = data.configSettings(id).firstOrNull { it.path == path }

    /** The number editor: the value in the middle, steps down on the left, steps up on the right. */
    fun number(player: MenuViewer, id: String, path: String, back: (MenuViewer) -> Unit, write: SettingWriter, ask: (MenuViewer, ConfigSetting) -> Unit) {
        val setting = setting(id, path)?.takeIf { it.value?.toDoubleOrNull() != null } ?: return back(player)
        val current = requireNotNull(setting.value).toDouble()
        val builder = frame(Menus.chest(title(setting.title.take(TITLE_LIMIT))).rows(SMALL_ROWS + 1), SMALL_ROWS + 1)
        val again = { who: MenuViewer -> number(who, id, path, back, write, ask) }
        builder.slot(VALUE, ConfigLook.setting(setting, true))
        ConfigNumbers.ladder(setting).forEachIndexed { index, (label, amount) ->
            builder.button(VALUE - 1 - index, stepItem("−", label, true), go { who -> write(who, setting, text(setting, current - amount), again) })
            builder.button(VALUE + 1 + index, stepItem("+", label, false), go { who -> write(who, setting, text(setting, current + amount), again) })
        }
        builder.button(
            TYPE,
            icon(Icons.CHANGELOG, "NAME_TAG", "Ввести значение", Style.lore(typingHelp(setting), emptyList(), Style.left("написать в чате"))),
            go { who -> ask(who, setting) },
        )
        setting.min?.let { limit -> builder.button(LOW, edge("Минимум", limit, setting), go { who -> write(who, setting, text(setting, limit), again) }) }
        setting.max?.let { limit -> builder.button(HIGH, edge("Максимум", limit, setting), go { who -> write(who, setting, text(setting, limit), again) }) }
        builder.button(SMALL_BACK, backItem(), go { back(it) })
        show(player, builder.build())
    }

    private fun typingHelp(setting: ConfigSetting): List<String> {
        val base = if (setting.kind == ConfigSettingKind.INTEGER) "Целое число." else "Число, дробная часть через точку."
        val time = if (setting.duration != null) " Время можно писать так: 90m, 1h30m, 45с, 2д." else ""
        return wrap(base + time, WRAP_WIDTH, 3)
    }

    private fun stepItem(sign: String, label: String, down: Boolean): ItemSpec =
        icon(null, if (down) "RED_DYE" else "LIME_DYE", "$sign $label", Style.lore(emptyList(), emptyList(), Style.left(if (down) "уменьшить" else "увеличить")), danger = down, good = !down)

    private fun edge(name: String, limit: Double, setting: ConfigSetting): ItemSpec =
        icon(Icons.CLOCK, "CLOCK", name, Style.lore(emptyList(), listOf("Значение" to ConfigNumbers.shown(setting.copy(value = ConfigLook.number(limit)))), Style.left("поставить")))

    /** Rounds to what the field holds: whole numbers without a fraction, decimals to thousandths. */
    private fun text(setting: ConfigSetting, value: Double): String {
        val bounded = value.coerceIn(setting.min ?: -Double.MAX_VALUE, setting.max ?: Double.MAX_VALUE)
        return if (setting.kind == ConfigSettingKind.INTEGER) Math.round(bounded).toString() else ConfigLook.number(Math.round(bounded * MILLI) / MILLI)
    }

    /** All choices of a setting in pages; a click picks one and returns to [done]. */
    fun choice(player: MenuViewer, id: String, path: String, page: Int, done: (MenuViewer) -> Unit, write: SettingWriter) {
        val setting = setting(id, path) ?: return done(player)
        val rows = setting.choices.map { value ->
            val chosen = value == setting.value
            val lore = Style.lore(emptyList(), emptyList(), *(if (chosen) emptyArray() else arrayOf(Style.left("выбрать"))))
            Row(
                icon(null, if (chosen) "LIME_DYE" else "PAPER", value, lore, good = chosen),
                if (chosen) null else go { who -> write(who, setting, value, done) },
            )
        }
        list(
            player, setting.title.take(TITLE_LIMIT), rows, page, PAGE_SIZE, { who, to -> choice(who, id, path, to, done, write) }, done, "Вариантов нет",
            extra = { it.slot(DATE_FILTER, ConfigLook.place("Настройка" to setting.title, "Сейчас" to setting.value.orEmpty(), "Вариантов" to "${setting.choices.size}")) },
        )
    }

    private companion object {
        const val VALUE = 13
        const val TYPE = 4
        const val LOW = 20
        const val HIGH = 24
        const val MILLI = 1000.0
    }
}
