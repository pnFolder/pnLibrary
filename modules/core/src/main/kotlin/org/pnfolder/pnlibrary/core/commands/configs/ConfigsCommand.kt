package org.pnfolder.pnlibrary.core.commands.configs

import net.kyori.adventure.text.Component
import org.pnfolder.pnlibrary.api.chat.ChatPalette
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.commands.definition.ArgumentType
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition
import org.pnfolder.pnlibrary.api.commands.definition.CommandNodeBuilder
import org.pnfolder.pnlibrary.api.commands.definition.command
import org.pnfolder.pnlibrary.api.config.ConfigApply
import org.pnfolder.pnlibrary.api.config.ConfigEditException
import org.pnfolder.pnlibrary.api.config.ConfigId
import org.pnfolder.pnlibrary.api.config.ConfigInfo
import org.pnfolder.pnlibrary.api.config.ConfigPolymorphic
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.core.commands.engine.pageCount
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/**
 * `/pn configs`: every configuration file of every plugin under its identifier `plugin/module:path`.
 *
 * | Command | What it does |
 * |---|---|
 * | `/pn configs [page]` | lists the files with their class and state |
 * | `/pn configs info <id>` | one file: where it lies, whether it is loaded, what was skipped, problems |
 * | `/pn configs reload <id>` | reads the file again; the old value stays when the new one is broken |
 * | `/pn configs validate <id>` | checks the loaded value without writing anything |
 * | `/pn configs types <name>` | the words a polymorphic type understands and which plugin added each |
 * | `/pn configs settings <id>` | the settings of a file that may be changed in game (`@ConfigEditable`), with values |
 * | `/pn configs edit <id> <path> <value>` | changes one such setting and saves the file; needs `pnlibrary.configs.edit` |
 *
 * It never shows values from a file, so secrets cannot leak through it. Requires `pnlibrary.configs`; the console always may.
 */
class ConfigsCommand(private val library: PnLibrary) {
    fun definition(): CommandDefinition = command("configs") {
        permission(PERMISSION)
        noPermissionMessage("Конфигурации плагинов доступны администраторам. Нужно право $PERMISSION.")
        consoleBypassesPermission()
        executes { list(it, 1) }
        argument("page", ArgumentType.integer()) { executes { list(it, it.get<Int>("page")) } }
        literal("info") { withId { context, id -> info(context, id) } }
        literal("reload") { withId { context, id -> reload(context, id) } }
        literal("validate") { withId { context, id -> validate(context, id) } }
        literal("settings") { withId { context, id -> settings(context, id) } }
        literal("edit") {
            permission(EDIT_PERMISSION)
            noPermissionMessage("Менять настройки может тот, у кого есть право $EDIT_PERMISSION.")
            argument("id", ArgumentType.string()) {
                suggests { library.configurations.all().filter { !it.readOnly }.map { it.id.toString() } }
                argument("path", ArgumentType.string()) {
                    argument("value", ArgumentType.greedyString()) {
                        executes { context ->
                            val id = parseId(context) ?: return@executes
                            edit(context, id, context.get<String>("path"), context.get<String>("value"))
                        }
                    }
                }
            }
        }
        literal("types") {
            argument("type", ArgumentType.string()) {
                suggests { polymorphicBases().map { it.simpleName } }
                executes { types(it, it.get<String>("type")) }
            }
        }
    }

    private fun org.pnfolder.pnlibrary.api.commands.definition.CommandNodeBuilder.withId(action: (CommandContext, ConfigId) -> Unit) {
        argument("id", ArgumentType.string()) {
            suggests { library.configurations.all().map { it.id.toString() } }
            executes { context -> parseId(context)?.let { action(context, it) } }
        }
    }

    /** The file named by the `id` argument, or `null` after telling the sender what is wrong with it. */
    private fun parseId(context: CommandContext): ConfigId? {
        val typed = context.get<String>("id")
        val id = runCatching { ConfigId.parse(typed) }.getOrElse {
            say(context, "Неверный адрес «$typed»: ${it.message}", ChatPalette.DANGER)
            return null
        }
        if (library.configurations.get(id) == null) {
            say(context, "Такого файла нет: $id. Список: $ROOT", ChatPalette.DANGER)
            return null
        }
        return id
    }

    private fun say(context: CommandContext, text: String, color: net.kyori.adventure.text.format.TextColor = ChatPalette.TEXT) =
        context.sender.sendMessage(Component.text(text, color))

    private fun list(context: CommandContext, requested: Int) {
        val all = library.configurations.all()
        val pages = pageCount(all.size, PAGE_SIZE)
        val page = requested.coerceIn(1, pages)
        say(context, "Конфигурации: ${all.size}, страница $page/$pages", ChatPalette.ACCENT)
        all.drop((page - 1) * PAGE_SIZE).take(PAGE_SIZE).forEach { info ->
            val state = when {
                !info.exists -> "нет файла"
                info.skippedUnknownTypes.isNotEmpty() -> "пропущено элементов: ${info.skippedUnknownTypes.size}"
                info.isLoaded -> "загружен"
                else -> "не загружен"
            }
            val flags = if (info.readOnly) ", только чтение" else ""
            say(context, "${info.id}  ${info.modelType.simpleName}  ($state$flags)", if (info.isLoaded) ChatPalette.TEXT else ChatPalette.FAINT)
        }
        say(context, "Подробнее: $ROOT info <адрес>", ChatPalette.FAINT)
    }

    private fun info(context: CommandContext, id: ConfigId) {
        val info = requireNotNull(library.configurations.get(id)?.info)
        say(context, id.toString(), ChatPalette.ACCENT)
        say(context, "  плагин: ${info.plugin}, модуль: ${info.module}, корень: ${info.root.name.lowercase()}")
        say(context, "  файл: ${info.file}${if (info.exists) "" else " (не существует)"}")
        say(context, "  класс: ${info.modelType.name}")
        say(context, "  загружен: ${if (info.isLoaded) "да, ${info.loadedAt}" else "нет"}${if (info.readOnly) "; только чтение" else ""}")
        if (info.skippedUnknownTypes.isNotEmpty()) {
            say(context, "  пропущено из-за неизвестного type (остаётся в файле):", ChatPalette.WARN)
            info.skippedUnknownTypes.forEach { say(context, "    $it", ChatPalette.WARN) }
        }
        problems(context, info)
    }

    private fun problems(context: CommandContext, info: ConfigInfo) {
        if (info.problems.isEmpty()) return
        say(context, "  проблемы:", ChatPalette.DANGER)
        info.problems.forEach { say(context, "    ${it.path}: ${it.message}", ChatPalette.DANGER) }
    }

    private fun settings(context: CommandContext, id: ConfigId) {
        val config = requireNotNull(library.configurations.get(id))
        val list = config.settings
        if (list.isEmpty()) {
            say(context, "$id: нет настроек, которые можно менять в игре.", ChatPalette.FAINT)
            return
        }
        say(context, "$id: настроек ${list.size}", ChatPalette.ACCENT)
        list.forEach { setting ->
            val limits = listOfNotNull(setting.min?.let { "от ${trim(it)}" }, setting.max?.let { "до ${trim(it)}" }).joinToString(" ")
            val choices = if (setting.choices.isEmpty()) "" else " (${setting.choices.joinToString("|")})"
            val applies = when (setting.apply) {
                ConfigApply.LIVE -> ""
                ConfigApply.RELOAD -> "  [нужно перечитать]"
                ConfigApply.RESTART -> "  [нужен перезапуск]"
            }
            say(context, "  ${setting.path} = ${setting.value ?: "?"}$choices${if (limits.isEmpty()) "" else " ($limits)"}$applies")
            if (setting.description.isNotBlank()) say(context, "    ${setting.title}: ${setting.description}", ChatPalette.FAINT)
        }
        config.info?.pendingApply?.let { say(context, "Есть изменения, которые ещё не работают: ${applyWord(it)}.", ChatPalette.WARN) }
    }

    private fun trim(number: Double): String = if (number == Math.floor(number)) number.toLong().toString() else number.toString()

    private fun applyWord(apply: ConfigApply) = when (apply) {
        ConfigApply.LIVE -> "работают сразу"
        ConfigApply.RELOAD -> "нужно перечитать файл: $ROOT reload <адрес>"
        ConfigApply.RESTART -> "нужен перезапуск сервера"
    }

    private fun edit(context: CommandContext, id: ConfigId, path: String, value: String) {
        val config = requireNotNull(library.configurations.get(id))
        try {
            val result = config.edit(path, value)
            say(context, "$id: $path ${result.previous ?: "?"} → ${result.setting.value}. Файл сохранён.", ChatPalette.OK)
            if (result.setting.apply != ConfigApply.LIVE) say(context, "Начнёт работать: ${applyWord(result.setting.apply)}.", ChatPalette.WARN)
        } catch (error: ConfigEditException) {
            say(context, "Не изменено: ${error.message}", ChatPalette.DANGER)
        } catch (error: IllegalStateException) {
            say(context, "Не изменено: ${error.message}", ChatPalette.DANGER)
        }
    }

    private fun reload(context: CommandContext, id: ConfigId) {
        val config = requireNotNull(library.configurations.get(id))
        try {
            config.reload()
            say(context, "$id прочитан заново.", ChatPalette.OK)
            config.info?.takeIf { it.skippedUnknownTypes.isNotEmpty() }?.let {
                say(context, "Пропущено элементов с неизвестным type: ${it.skippedUnknownTypes.size}. Подробнее: $ROOT info $id", ChatPalette.WARN)
            }
        } catch (error: Exception) {
            say(context, "$id не прочитан, прежнее значение сохранено: ${error.message}", ChatPalette.DANGER)
        }
    }

    private fun validate(context: CommandContext, id: ConfigId) {
        val config = requireNotNull(library.configurations.get(id))
        if (!config.isLoaded) {
            say(context, "$id не загружен, проверять нечего.", ChatPalette.WARN)
            return
        }
        val problems = config.validate()
        if (problems.isEmpty()) say(context, "$id: проблем нет.", ChatPalette.OK)
        else {
            say(context, "$id: проблем ${problems.size}", ChatPalette.DANGER)
            problems.forEach { say(context, "  ${it.path}: ${it.message}", ChatPalette.DANGER) }
        }
    }

    private fun types(context: CommandContext, typed: String) {
        val base = polymorphicBases().firstOrNull { it.simpleName.equals(typed, ignoreCase = true) || it.name == typed }
        if (base == null) {
            say(context, "Такого типа нет среди зарегистрированных конфигураций: $typed", ChatPalette.DANGER)
            return
        }
        val words = library.configurations.types(base)
        say(context, "${base.simpleName}: слов ${words.size}", ChatPalette.ACCENT)
        words.forEach { word ->
            val who = word.owner?.let { "добавил $it в ${word.target}" } ?: "объявлен в самом типе"
            val aliases = if (word.aliases.isEmpty()) "" else " (также ${word.aliases.joinToString()})"
            say(context, "  ${word.name}$aliases → ${word.implementation.simpleName}; $who; приоритет ${word.priority}")
        }
    }

    /** The polymorphic types that the registered files use, found by looking through the fields of their classes. */
    private fun polymorphicBases(): List<Class<*>> {
        val seen = linkedSetOf<Class<*>>()
        val found = linkedSetOf<Class<*>>()
        fun visit(type: Type) {
            val raw = when (type) {
                is Class<*> -> type
                is ParameterizedType -> {
                    type.actualTypeArguments.forEach(::visit)
                    type.rawType as? Class<*>
                }
                // Kotlin writes List<Prize> as List<? extends Prize>.
                is java.lang.reflect.WildcardType -> {
                    type.upperBounds.forEach(::visit)
                    null
                }
                else -> null
            } ?: return
            if (raw.isPrimitive || raw.name.startsWith("java.") || !seen.add(raw)) return
            if (raw.isAnnotationPresent(ConfigPolymorphic::class.java)) found += raw
            raw.declaredFields.forEach { visit(it.genericType) }
        }
        library.configurations.all().forEach { visit(it.modelType) }
        return found.toList()
    }

    companion object {
        const val PERMISSION = "pnlibrary.configs"
        const val EDIT_PERMISSION = "pnlibrary.configs.edit"
        private const val ROOT = "/pn configs"
        private const val PAGE_SIZE = 10
    }
}
