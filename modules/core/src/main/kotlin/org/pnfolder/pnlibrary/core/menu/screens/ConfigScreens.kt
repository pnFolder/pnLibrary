package org.pnfolder.pnlibrary.core.menu.screens

import org.pnfolder.pnlibrary.api.chat.style.ChatTone
import org.pnfolder.pnlibrary.api.config.ConfigApply
import org.pnfolder.pnlibrary.api.config.ConfigEditException
import org.pnfolder.pnlibrary.api.config.ConfigId
import org.pnfolder.pnlibrary.api.config.ConfigSetting
import org.pnfolder.pnlibrary.api.config.ConfigSettingKind
import org.pnfolder.pnlibrary.core.menu.ClickType
import org.pnfolder.pnlibrary.core.menu.MenuRoutes
import org.pnfolder.pnlibrary.core.menu.MenuScreens
import org.pnfolder.pnlibrary.core.menu.MenuSession
import org.pnfolder.pnlibrary.core.menu.MenuViewer
import org.pnfolder.pnlibrary.core.menu.style.ConfigLook
import org.pnfolder.pnlibrary.core.menu.style.ConfigNumbers
import org.pnfolder.pnlibrary.core.menu.style.DATE_FILTER
import org.pnfolder.pnlibrary.core.menu.style.FILTER
import org.pnfolder.pnlibrary.core.menu.style.Icons
import org.pnfolder.pnlibrary.core.menu.style.PAGE_SIZE
import org.pnfolder.pnlibrary.core.menu.style.Row
import org.pnfolder.pnlibrary.core.menu.style.Style
import org.pnfolder.pnlibrary.core.menu.style.TITLE_LIMIT
import org.pnfolder.pnlibrary.core.menu.style.icon
import org.pnfolder.pnlibrary.core.text.WRAP_WIDTH
import org.pnfolder.pnlibrary.core.text.wrapText

private const val CONFIGS_EDIT = "pnlibrary.configs.edit"

/** One configuration file in the menu list. */
data class ConfigRow(
    val id: String,
    val model: String,
    val loaded: Boolean,
    val readOnly: Boolean,
    /** How many settings of the file may be changed in game. */
    val editable: Int,
    /** What the changes made since the last read still wait for, or null. */
    val pending: ConfigApply?,
)

/**
 * Menu screens for configuration files: plugins, their modules, their files, and the settings of one file that a
 * plugin marked as editable. A switch flips on a click, a choice goes round, a number goes up and down, and a text is
 * typed in chat. How each button looks is [ConfigLook].
 */
internal class ConfigScreens(session: MenuSession, routes: MenuRoutes) : MenuScreens(session, routes) {
    /** What the first level lists: the plugins, or every file of every plugin with its plugin and module named. */
    internal enum class Top(val label: String) { PLUGINS("Выбрать плагин"), ALL("Все конфигурации") }

    /** How the files of one plugin are laid out: grouped by module, or all of them in one list. */
    internal enum class View(val label: String) { MODULES("По модулям"), FILES("Все файлы плагина") }

    private val pickers = ConfigPickers(session, routes)

    private data class Located(val file: ConfigRow, val plugin: String, val module: String, val path: String)

    private fun located(): List<Located> = data.configs().map {
        val id = ConfigId.parse(it.id)
        Located(it, id.plugin.value, id.module.value, id.path)
    }

    private fun waiting(files: List<Located>): ConfigApply? = files.mapNotNull { it.file.pending }.maxOrNull()

    /** The first level of the section. */
    fun configs(player: MenuViewer, page: Int) = configsBy(player, Top.PLUGINS, page)

    fun configsBy(player: MenuViewer, top: Top, page: Int) {
        val all = located()
        val rows = if (top == Top.PLUGINS) all.groupBy { it.plugin }.toSortedMap().map { (plugin, files) -> pluginRow(plugin, files, top) }
        else all.sortedWith(compareBy({ it.plugin }, { it.module }, { it.path })).map { fileRow(it, true, true) { who -> configsBy(who, top, page) } }
        val other = Top.entries.first { it != top }
        list(
            player, "Конфигурации", rows, page, PAGE_SIZE, { who, to -> configsBy(who, top, to) }, { routes.home(it) }, "Конфигураций нет",
            extra = {
                val switch = switcher("Показать", "Выбрать плагин или сразу увидеть все файлы.", Top.entries.map { t -> t.label }, top.ordinal)
                it.button(FILTER, switch, go { who -> configsBy(who, other, 0) })
                it.slot(DATE_FILTER, ConfigLook.place("Плагинов" to "${all.map { f -> f.plugin }.distinct().size}", "Файлов" to "${all.size}"))
            },
        )
    }

    private fun switcher(label: String, description: String, options: List<String>, selected: Int) = icon(
        Icons.SETTINGS, "HOPPER", "$label: ${options[selected]}",
        Style.lore(wrapText(description, WRAP_WIDTH, 2), emptyList(), Style.left("переключить"), extra = Style.choices(options, selected)),
    )

    private fun pluginRow(plugin: String, files: List<Located>, top: Top): Row = Row(
        ConfigLook.plugin(plugin, files.map { it.module }.distinct().size, files.size, files.sumOf { it.file.editable }, waiting(files)),
        go { who -> pluginFiles(who, plugin, null, 0, top) },
    )

    /**
     * The second level: one plugin. The switch button picks between its modules and every file of the plugin
     * with its module named; a plugin with one module opens on its files.
     */
    fun pluginFiles(player: MenuViewer, plugin: String, view: View?, page: Int, top: Top) {
        val files = located().filter { it.plugin == plugin }
        val modules = files.map { it.module }.distinct().sorted()
        val shown = view ?: if (modules.size > 1) View.MODULES else View.FILES
        val rows = if (shown == View.MODULES) modules.map { moduleRow(plugin, it, files.filter { f -> f.module == it }, top) }
        else {
            files.sortedWith(compareBy({ it.module }, { it.path }))
                .map { fileRow(it, false, modules.size > 1) { who -> pluginFiles(who, plugin, shown, page, top) } }
        }
        val switch = View.entries.first { it != shown }
        list(
            player, plugin, rows, page, PAGE_SIZE, { who, to -> pluginFiles(who, plugin, shown, to, top) }, { configsBy(it, top, 0) },
            "У плагина нет конфигураций",
            extra = {
                it.button(
                    FILTER,
                    switcher("Показать", "Модули плагина или все его файлы подряд.", View.entries.map { v -> v.label }, shown.ordinal),
                    go { who -> pluginFiles(who, plugin, switch, 0, top) },
                )
                it.slot(DATE_FILTER, ConfigLook.place("Плагин" to plugin, "Модулей" to "${modules.size}", "Файлов" to "${files.size}"))
            },
        )
    }

    /** The third level: the files of one module. */
    fun moduleFiles(player: MenuViewer, plugin: String, module: String, page: Int, top: Top) {
        val files = located().filter { it.plugin == plugin && it.module == module }
        val rows = files.sortedBy { it.path }.map { fileRow(it, false, false) { who -> moduleFiles(who, plugin, module, page, top) } }
        list(
            player, "$plugin · $module".take(TITLE_LIMIT), rows, page, PAGE_SIZE, { who, to -> moduleFiles(who, plugin, module, to, top) },
            { pluginFiles(it, plugin, View.MODULES, 0, top) }, "В модуле нет конфигураций",
            extra = { it.slot(DATE_FILTER, ConfigLook.place("Плагин" to plugin, "Модуль" to module, "Файлов" to "${files.size}")) },
        )
    }

    private fun moduleRow(plugin: String, module: String, files: List<Located>, top: Top): Row = Row(
        ConfigLook.module(plugin, module, files.size, files.sumOf { it.file.editable }, waiting(files)),
        go { who -> moduleFiles(who, plugin, module, 0, top) },
    )

    private fun fileRow(entry: Located, withPlugin: Boolean, withModule: Boolean, back: (MenuViewer) -> Unit): Row {
        val file = entry.file
        val item = ConfigLook.file(file, entry.plugin, entry.module, entry.path, withPlugin, withModule)
        return Row(item, if (file.editable > 0) go { who -> card(who, file.id, 0, back) } else null)
    }

    /** The settings of one file; [back] returns to the screen it was opened from. */
    fun card(player: MenuViewer, id: String, page: Int, back: (MenuViewer) -> Unit) {
        val file = data.configs().firstOrNull { it.id == id }
        val address = ConfigId.parse(id)
        val rows = data.configSettings(id).map { setting -> row(id, setting, file?.readOnly == true, page, back) }
        list(
            player, "${address.module.value} · ${address.path.substringAfterLast('/')}".take(TITLE_LIMIT), rows, page, PAGE_SIZE,
            { who, to -> card(who, id, to, back) }, back, "В этом файле нет настроек для игры",
            extra = {
                it.slot(
                    DATE_FILTER,
                    ConfigLook.place(
                        "Плагин" to address.plugin.value,
                        "Модуль" to address.module.value,
                        "Файл" to address.path,
                        "Класс" to file?.model.orEmpty(),
                    ),
                )
            },
        )
    }

    private fun row(id: String, setting: ConfigSetting, readOnly: Boolean, page: Int, back: (MenuViewer) -> Unit): Row {
        val click = if (readOnly) null else goClick { who, how -> change(who, id, setting, how, page, back) }
        return Row(ConfigLook.setting(setting, readOnly), click)
    }

    private fun change(player: MenuViewer, id: String, setting: ConfigSetting, how: ClickType, page: Int, back: (MenuViewer) -> Unit) {
        val here = { who: MenuViewer -> card(who, id, page, back) }
        val numeric = setting.kind == ConfigSettingKind.INTEGER || setting.kind == ConfigSettingKind.DECIMAL
        when {
            setting.kind == ConfigSettingKind.TEXT -> allowed(player, CONFIGS_EDIT) { ask(player, id, setting) }
            numeric && how == ClickType.MIDDLE -> allowed(
                player,
                CONFIGS_EDIT,
            ) { pickers.number(player, id, setting.path, here, writer(id), { who, item -> ask(who, id, item) }) }
            setting.kind == ConfigSettingKind.CHOICE && how.isShift() && setting.choices.size > 2 ->
                allowed(player, CONFIGS_EDIT) { pickers.choice(player, id, setting.path, 0, here, writer(id)) }
            else -> next(setting, how)?.let { value -> allowed(player, CONFIGS_EDIT) { write(player, id, setting, value, here) } }
        }
    }

    private fun ClickType.isShift() = this == ClickType.SHIFT_LEFT || this == ClickType.SHIFT_RIGHT

    private fun writer(id: String): SettingWriter = { who, setting, value, after -> write(who, id, setting, value, after) }

    private fun ask(player: MenuViewer, id: String, setting: ConfigSetting) {
        val hint = listOf(
            setting.format.takeIf(String::isNotBlank)?.let { "Формат: $it." },
            setting.examples.takeIf { it.isNotEmpty() }?.let { "Например: ${it.joinToString()}." },
        )
            .filterNotNull().joinToString(" ")
        suggestCommand(player, "Введите новое значение для «${setting.title}». $hint".trim(), "/pn configs edit $id ${setting.path} ${setting.value.orEmpty()}")
    }

    private fun write(player: MenuViewer, id: String, setting: ConfigSetting, next: String, after: (MenuViewer) -> Unit) {
        host.async {
            val outcome = try {
                data.editConfig(id, setting.path, next)
                null
            } catch (error: ConfigEditException) {
                error.message
            } catch (error: Exception) {
                error.message ?: "Не удалось записать файл"
            }
            host.sync {
                if (outcome != null) tell(player, ChatTone.FAILURE, "Не изменено.", outcome)
                else if (setting.apply != ConfigApply.LIVE) {
                    tell(player, ChatTone.WARNING, "Сохранено: ${setting.title} = $next.", "Заработает: ${ConfigLook.applyWord(setting.apply)}.")
                }
                after(player)
            }
        }
    }

    /** The value a click moves to, or `null` when the click does nothing. */
    private fun next(setting: ConfigSetting, how: ClickType): String? {
        val current = setting.value ?: return null
        val backwards = how == ClickType.RIGHT || how == ClickType.SHIFT_RIGHT
        val big = how == ClickType.SHIFT_LEFT || how == ClickType.SHIFT_RIGHT
        return when (setting.kind) {
            ConfigSettingKind.BOOLEAN -> (current != "true").toString()
            ConfigSettingKind.CHOICE -> {
                val at = setting.choices.indexOf(current)
                val step = if (backwards) -1 else 1
                setting.choices[Math.floorMod(at + step, setting.choices.size)]
            }
            ConfigSettingKind.INTEGER -> {
                val step = ConfigNumbers.ladder(setting)[if (big) 1 else 0].second * (if (backwards) -1 else 1)
                clamp((current.toLongOrNull() ?: return null) + step, setting).let { Math.round(it).toString() }
            }
            ConfigSettingKind.DECIMAL -> {
                val step = ConfigNumbers.ladder(setting)[if (big) 1 else 0].second * (if (backwards) -1 else 1)
                ConfigLook.number(clamp(Math.round(((current.toDoubleOrNull() ?: return null) + step) * 1000.0) / 1000.0, setting))
            }
            ConfigSettingKind.TEXT -> null
        }
    }

    private fun clamp(value: Double, setting: ConfigSetting): Double = value.coerceIn(setting.min ?: -Double.MAX_VALUE, setting.max ?: Double.MAX_VALUE)
}
