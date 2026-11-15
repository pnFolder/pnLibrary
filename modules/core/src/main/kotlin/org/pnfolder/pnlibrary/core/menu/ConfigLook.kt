package org.pnfolder.pnlibrary.core.menu

import org.pnfolder.pnlibrary.api.config.ConfigApply
import org.pnfolder.pnlibrary.api.config.ConfigSetting
import org.pnfolder.pnlibrary.api.config.ConfigSettingKind

/**
 * How the configuration screens look: the icon, the name and the lines of every kind of button.
 *
 * Names stay short (a file shows its own name, not its full address); everything else goes into the description and
 * into labelled facts. A long value is broken into short lines under its label, so it never runs off the item.
 */
internal object ConfigLook {
    private const val LINE_WIDTH = 38
    private const val LABEL_FRAME = 4
    private const val CONTINUATION_FRAME = 5
    private const val MIN_WIDTH = 12
    private const val FACT_LINES = 5
    private const val BAR_WIDTH = 12
    private const val PERCENT = 100
    private const val BREAKS = " /\\.-_:,"
    private val RED = MenuColors.color("&#FF7A7A")
    private const val CHOICES_SHOWN = 8
    private val GREEN = MenuColors.color("&#6EE7A0")
    private val GRAY = MenuColors.color("&#8B7A99")
    private val AMBER = MenuColors.color("&#FFB347")

    fun applyWord(apply: ConfigApply) = when (apply) {
        ConfigApply.LIVE -> "сразу"
        ConfigApply.RELOAD -> "после перечитывания файла"
        ConfigApply.RESTART -> "после перезапуска"
    }

    fun number(value: Double): String = if (value == Math.floor(value) && Math.abs(value) < 1e15) value.toLong().toString() else value.toString()

    /**
     * One fact; a long value continues on the lines below the label. The value is broken at spaces and after `/ \ . - _ :`,
     * so a path or a class name is cut between its parts and never inside a word.
     */
    fun fact(label: String, value: String): List<Pair<String, String>> {
        val lines = softWrap(value, LINE_WIDTH - label.length - LABEL_FRAME, LINE_WIDTH - CONTINUATION_FRAME)
        val kept = if (lines.size > FACT_LINES) lines.take(FACT_LINES - 1) + "${lines[FACT_LINES - 1]}…" else lines
        return kept.mapIndexed { index, line -> (if (index == 0) label else "") to line }
    }

    /** Breaks [text] into lines that fit [firstWidth] and then [width]; a piece longer than a line is cut only as the last resort. */
    fun softWrap(text: String, firstWidth: Int, width: Int): List<String> {
        val lines = ArrayList<String>()
        var line = StringBuilder()
        var limit = firstWidth.coerceAtLeast(MIN_WIDTH)
        fun flush() {
            lines += line.toString().trimEnd()
            line = StringBuilder()
            limit = width
        }
        for (piece in pieces(text)) {
            val size = piece.trimEnd().length
            if (line.isNotEmpty() && line.length + size > limit) flush()
            var rest = piece
            while (rest.trimEnd().length > limit) {
                if (line.isNotEmpty()) flush()
                lines += rest.take(limit)
                rest = rest.drop(limit)
                limit = width
            }
            line.append(rest)
        }
        if (line.isNotEmpty() || lines.isEmpty()) lines += line.toString().trimEnd()
        return lines
    }

    private fun pieces(text: String): List<String> {
        val result = ArrayList<String>()
        val current = StringBuilder()
        for (char in text) {
            current.append(char)
            if (char in BREAKS) {
                result += current.toString()
                current.clear()
            }
        }
        if (current.isNotEmpty()) result += current.toString()
        return result
    }

    private fun facts(vararg pairs: Pair<String, String>): List<Pair<String, String>> = pairs.flatMap { (label, value) -> fact(label, value) }

    private fun about(text: String): List<String> = wrap(text, WRAP_WIDTH, 3)

    private fun editableWord(count: Int) = if (count > 0) "$count" else "нет"

    private fun pending(apply: ConfigApply?): List<Pair<String, String>> = apply?.let { listOf("Ждёт" to AMBER + applyWord(it)) }.orEmpty()

    fun plugin(name: String, modules: Int, files: Int, editable: Int, waiting: ConfigApply?): ItemSpec = icon(
        Icons.PLUGIN, "BOOKSHELF", name,
        Style.lore(
            about("Конфигурации плагина. Откройте, чтобы выбрать модуль или файл."),
            facts("Модулей" to "$modules", "Файлов" to "$files", "Можно менять" to editableWord(editable)) + pending(waiting),
            Style.left("открыть"),
        ),
        warn = waiting != null,
    )

    fun module(plugin: String, module: String, files: Int, editable: Int, waiting: ConfigApply?): ItemSpec = icon(
        Icons.RELEASES, "CHEST", module,
        Style.lore(
            about("Модуль плагина $plugin. Внутри его файлы настроек."),
            facts("Файлов" to "$files", "Можно менять" to editableWord(editable)) + pending(waiting),
            Style.left("файлы модуля"),
        ),
        warn = waiting != null,
    )

    /** A file: its own name as the title; the plugin and module only when the list mixes several. */
    fun file(row: ConfigRow, plugin: String, module: String, path: String, withPlugin: Boolean, withModule: Boolean): ItemSpec {
        val name = path.substringAfterLast('/')
        val folder = path.substringBeforeLast('/', "")
        val shown = buildList {
            if (withPlugin) addAll(fact("Плагин", plugin))
            if (withModule) addAll(fact("Модуль", module))
            if (folder.isNotEmpty()) addAll(fact("Папка", folder))
            addAll(fact("Класс", row.model))
            add("Состояние" to if (row.loaded) "$GREEN● загружен" else "$GRAY○ не загружен")
            add("Можно менять" to editableWord(row.editable))
            if (row.readOnly) add("Режим" to "только чтение")
            addAll(pending(row.pending))
        }
        val text = if (row.editable > 0) "Настройки, которые можно менять в игре." else "В этом файле нет настроек для игры. Его правят вручную."
        val hint = if (row.editable > 0) arrayOf(Style.left("настройки")) else emptyArray()
        return icon(if (row.readOnly) Icons.LOCKED else Icons.CHANGELOG, if (row.readOnly) "BARRIER" else "WRITABLE_BOOK", name, Style.lore(about(text), shown, *hint), good = row.editable > 0 && row.loaded, warn = row.pending != null, muted = row.editable == 0)
    }

    /** One setting: the value, what it may be, when a change starts to work, and what a click does. */
    fun setting(setting: ConfigSetting, readOnly: Boolean): ItemSpec {
        val numeric = setting.kind == ConfigSettingKind.INTEGER || setting.kind == ConfigSettingKind.DECIMAL
        val shown = buildList {
            when {
                setting.kind == ConfigSettingKind.BOOLEAN -> add("Состояние" to if (setting.value == "true") "$GREEN✔ включено" else "$RED✘ выключено")
                numeric -> addAll(fact("Значение", ConfigNumbers.shown(setting)))
                else -> addAll(fact("Значение", setting.value ?: "?"))
            }
            if (setting.choices.size > 1) add("Вариант" to "${setting.choices.indexOf(setting.value) + 1} из ${setting.choices.size}")
            if (setting.min != null || setting.max != null) add("Границы" to "${setting.min?.let(::number) ?: "…"} … ${setting.max?.let(::number) ?: "…"}")
            scale(setting)?.let { add("Шкала" to it) }
            if (numeric) addAll(ConfigNumbers.ladder(setting).take(2).let { (one, ten) -> listOf("Шаг" to one.first, "С Shift" to ten.first) })
            if (setting.format.isNotBlank()) addAll(fact("Формат", setting.format))
            if (setting.examples.isNotEmpty()) addAll(fact("Примеры", setting.examples.joinToString(", ")))
            if (setting.apply != ConfigApply.LIVE) add("Заработает" to AMBER + applyWord(setting.apply))
        }
        val description = setting.description.takeIf(String::isNotBlank)?.let { wrap(it, WRAP_WIDTH, 4) }.orEmpty()
        val (texture, material) = when (setting.kind) {
            ConfigSettingKind.BOOLEAN -> Icons.TOGGLE to if (setting.value == "true") "LIME_DYE" else "GRAY_DYE"
            ConfigSettingKind.CHOICE -> Icons.SETTINGS to "COMPARATOR"
            ConfigSettingKind.INTEGER, ConfigSettingKind.DECIMAL -> Icons.CLOCK to if (setting.duration != null) "CLOCK" else "REPEATER"
            ConfigSettingKind.TEXT -> Icons.CHANGELOG to "NAME_TAG"
        }
        return icon(
            texture, material, setting.title,
            Style.lore(description, shown, *hints(setting, readOnly), extra = choices(setting)),
            good = setting.kind == ConfigSettingKind.BOOLEAN && setting.value == "true",
            muted = setting.kind == ConfigSettingKind.BOOLEAN && setting.value != "true",
        )
    }

    /** Where a number sits between its bounds, as a bar; only when both bounds are known. */
    private fun scale(setting: ConfigSetting): String? {
        val low = setting.min ?: return null
        val high = setting.max?.takeIf { it > low } ?: return null
        val value = setting.value?.toDoubleOrNull() ?: return null
        val share = ((value - low) / (high - low)).coerceIn(0.0, 1.0)
        val filled = Math.round(share * BAR_WIDTH).toInt()
        return GREEN + "█".repeat(filled) + GRAY + "░".repeat(BAR_WIDTH - filled) + " ${Math.round(share * PERCENT)}%"
    }

    private fun hints(setting: ConfigSetting, readOnly: Boolean): Array<String> = if (readOnly) emptyArray() else when (setting.kind) {
        ConfigSettingKind.BOOLEAN -> arrayOf(Style.left("включить или выключить"))
        ConfigSettingKind.CHOICE ->
            if (setting.choices.size > CHOICES_SHOWN) arrayOf(Style.left("следующий"), Style.right("предыдущий"), Style.shiftLeft("весь список"))
            else arrayOf(Style.left("следующий вариант"), Style.right("предыдущий"))
        ConfigSettingKind.INTEGER, ConfigSettingKind.DECIMAL ->
            arrayOf(Style.left("больше"), Style.right("меньше"), Style.shiftLeft("в десять раз сильнее"), Style.middle("ввести число"))
        ConfigSettingKind.TEXT -> arrayOf(Style.left("ввести текст в чате"))
    }

    /** The choices one under another with the current one marked; a long list shows the window around it. */
    private fun choices(setting: ConfigSetting): List<String> {
        if (setting.choices.isEmpty()) return emptyList()
        val at = setting.choices.indexOf(setting.value).coerceAtLeast(0)
        val from = (at - CHOICES_SHOWN / 2).coerceIn(0, (setting.choices.size - CHOICES_SHOWN).coerceAtLeast(0))
        val window = setting.choices.drop(from).take(CHOICES_SHOWN)
        val lines = Style.choices(window, at - from)
        val above = from
        val below = setting.choices.size - from - window.size
        return buildList {
            if (above > 0) add("$GRAY   ▲ выше ещё $above")
            addAll(lines)
            if (below > 0) add("$GRAY   ▼ ниже ещё $below")
        }
    }

    /** The "where you are" item of a screen: the path down to the plugin, module or file. */
    fun place(vararg pairs: Pair<String, String>): ItemSpec =
        icon(Icons.PLUGINS, "COMPASS", "Где вы", Style.lore(emptyList(), facts(*pairs)))
}
