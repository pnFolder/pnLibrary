package org.pnfolder.pnlibrary.core.commands.settings

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import org.pnfolder.pnlibrary.api.chat.ChatKit
import org.pnfolder.pnlibrary.api.chat.style.ChatPalette
import org.pnfolder.pnlibrary.api.chat.ChatReader
import org.pnfolder.pnlibrary.api.commands.definition.ArgumentType
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition
import org.pnfolder.pnlibrary.api.commands.definition.command
import org.pnfolder.pnlibrary.core.commands.engine.chatPager
import org.pnfolder.pnlibrary.core.commands.engine.pageCount
import org.pnfolder.pnlibrary.core.settings.SettingApply
import org.pnfolder.pnlibrary.core.settings.SettingChange
import org.pnfolder.pnlibrary.core.settings.SettingException
import org.pnfolder.pnlibrary.core.settings.SettingKind
import org.pnfolder.pnlibrary.core.settings.SettingPickerHook
import org.pnfolder.pnlibrary.core.settings.SettingTexts
import org.pnfolder.pnlibrary.core.settings.SettingValue
import org.pnfolder.pnlibrary.core.settings.SettingsRegistry
import org.pnfolder.pnlibrary.core.settings.SettingsStore

/**
 * `/pn settings`: every setting of the library, changed in game; there is no settings file to edit.
 *
 * | Command | What it does |
 * |---|---|
 * | `/pn settings [list] [page]` | all settings with their values |
 * | `/pn settings get <key>` | one setting: value, default, limits, when a change starts to work |
 * | `/pn settings set <key> <value>` | changes a setting and saves it in `data/settings.json` |
 * | `/pn settings reset <key>` | returns a setting to its default |
 * | `/pn settings pick <key> [filter]` | a player opens the menu list of a choice setting, narrowed to the names that contain the filter |
 *
 * Requires `pnlibrary.settings`; the console always may. The list of settings is [SettingsRegistry].
 */
internal class SettingsCommand(private val settings: SettingsStore, private val picker: SettingPickerHook = SettingPickerHook()) {
    fun definition(): CommandDefinition = command("settings") {
        permission(PERMISSION)
        noPermissionMessage("Настройки библиотеки доступны администраторам. Нужно право $PERMISSION.")
        consoleBypassesPermission()
        executes { list(it, 1) }
        argument("page", ArgumentType.integer()) { executes { list(it, it.get<Int>("page")) } }
        literal("list") {
            executes { list(it, 1) }
            argument("page", ArgumentType.integer()) { executes { list(it, it.get<Int>("page")) } }
        }
        literal("get") {
            argument("key", ArgumentType.string()) {
                suggests { keys() }
                executes { context -> show(context, context.get<String>("key")) }
            }
        }
        literal("set") {
            argument("key", ArgumentType.string()) {
                suggests { keys() }
                argument("value", ArgumentType.greedyString()) {
                    suggests { context -> values(context.parsedValues["key"] as? String ?: context.arguments.getOrNull(1)) }
                    executes { context -> change(context, context.get<String>("key")) { settings.set(it, context.get<String>("value")) } }
                }
            }
        }
        literal("pick") {
            argument("key", ArgumentType.string()) {
                suggests { choiceKeys() }
                executes { context -> pick(context, context.get<String>("key"), "") }
                argument("filter", ArgumentType.greedyString()) {
                    executes { context -> pick(context, context.get<String>("key"), context.get<String>("filter")) }
                }
            }
        }
        literal("reset") {
            argument("key", ArgumentType.string()) {
                suggests { settings.values().filter { it.changed }.map { it.spec.key } }
                executes { context -> change(context, context.get<String>("key"), settings::reset) }
            }
        }
    }

    private fun keys(): List<String> = SettingsRegistry.all.map { it.key }

    private fun choiceKeys(): List<String> = SettingsRegistry.all.filter { it.choices != null }.map { it.key }

    /** Opens the menu list of the choice setting [key] for the player, narrowed to [filter]. */
    private fun pick(context: CommandContext, key: String, filter: String) {
        val spec = SettingsRegistry[key]?.takeIf { it.choices != null }
            ?: return say(context, "Нет настройки с выбором из списка: $key. Есть: ${choiceKeys().joinToString()}.", ChatPalette.DANGER)
        if (context.sender.isConsole) return say(context, "Список открывается в меню игрока. В консоли: $ROOT set ${spec.key} <значение>.", ChatPalette.WARN)
        val opened = picker.opener?.invoke(context, spec.key, filter.trim()) ?: false
        if (!opened) say(context, "Меню здесь недоступно. Откройте /pn gui на игровом сервере или используйте $ROOT set ${spec.key} <значение>.", ChatPalette.WARN)
    }

    /** What is worth proposing for the value of [key]: the words of a switch, the examples, the default. */
    private fun values(key: String?): List<String> {
        val spec = key?.let { SettingsRegistry[it] } ?: return emptyList()
        val proposals = when (spec.kind) {
            SettingKind.SWITCH -> listOf(SettingTexts.switchWord(true), SettingTexts.switchWord(false))
            SettingKind.NUMBER -> listOfNotNull(spec.min?.toString(), spec.max?.toString())
            SettingKind.TEXT, SettingKind.TIME -> spec.examples
            SettingKind.CHOICE -> spec.examples + spec.choices?.all.orEmpty()
        }
        return (listOf(spec.display(spec.default)) + proposals).distinct()
    }

    private fun say(context: CommandContext, text: String, color: TextColor = ChatPalette.TEXT) =
        context.sender.send(Component.text(text, color))

    private fun list(context: CommandContext, requested: Int) {
        val all = settings.values()
        val pages = pageCount(all.size, PAGE_SIZE)
        val page = requested.coerceIn(1, pages)
        say(context, "Настройки библиотеки: ${all.size}, страница $page/$pages", ChatPalette.ACCENT)
        all.drop((page - 1) * PAGE_SIZE).take(PAGE_SIZE).forEach { entry ->
            val mark = if (entry.changed) "  (по умолчанию ${entry.spec.display(entry.spec.default)})" else ""
            say(context, "${entry.spec.key} = ${entry.spec.display(entry.current)}$mark", if (entry.changed) ChatPalette.OK else ChatPalette.TEXT)
        }
        if (pages > 1) context.sender.send(chatPager(ChatKit(ChatReader.of(context.sender)), "$ROOT list", page, pages))
        say(context, "Подробнее: $ROOT get <ключ>. Изменить: $ROOT set <ключ> <значение>.", ChatPalette.FAINT)
    }

    private fun show(context: CommandContext, key: String) {
        val entry = try {
            settings.value(key)
        } catch (error: SettingException) {
            return say(context, error.message.orEmpty(), ChatPalette.DANGER)
        }
        describe(entry).forEach { (text, color) -> say(context, text, color) }
    }

    private fun describe(entry: SettingValue): List<Pair<String, TextColor>> {
        val spec = entry.spec
        return buildList {
            add(spec.key to ChatPalette.ACCENT)
            add("  значение: ${spec.display(entry.current)}${if (entry.changed) "" else " (по умолчанию)"}" to ChatPalette.TEXT)
            if (entry.changed) add("  по умолчанию: ${spec.display(spec.default)}" to ChatPalette.TEXT)
            add("  тип: ${spec.kind.label}" to ChatPalette.TEXT)
            spec.range().takeIf { it.isNotEmpty() }?.let { add("  допустимо: $it" to ChatPalette.TEXT) }
            if (spec.examples.isNotEmpty()) add("  примеры: ${spec.examples.joinToString()}" to ChatPalette.TEXT)
            add("  заработает: ${spec.applies.label}" to if (spec.applies == SettingApply.LIVE) ChatPalette.TEXT else ChatPalette.WARN)
            add("  ${spec.description}" to ChatPalette.FAINT)
        }
    }

    private fun change(context: CommandContext, key: String, action: (String) -> SettingChange) {
        try {
            val result = action(key)
            val spec = result.spec
            say(context, "${spec.key}: ${spec.display(result.before)} → ${spec.display(result.after)}. Сохранено.", ChatPalette.OK)
            if (spec.applies == SettingApply.RESTART) say(context, "Начнёт работать ${spec.applies.label} сервера.", ChatPalette.WARN)
        } catch (error: SettingException) {
            say(context, "Не изменено: ${error.message}", ChatPalette.DANGER)
        }
    }

    companion object {
        const val PERMISSION = "pnlibrary.settings"
        private const val ROOT = "/pn settings"
        private const val PAGE_SIZE = 6
    }
}
