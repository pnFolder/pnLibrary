package org.pnfolder.pnlibrary.core.commands.modules

import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.TextColor
import org.pnfolder.pnlibrary.api.chat.ChatKit
import org.pnfolder.pnlibrary.api.chat.ChatPalette
import org.pnfolder.pnlibrary.api.chat.ChatReader
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.commands.definition.ArgumentType
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition
import org.pnfolder.pnlibrary.api.commands.definition.CommandNodeBuilder
import org.pnfolder.pnlibrary.api.commands.definition.command
import org.pnfolder.pnlibrary.api.libraries.ModuleLibrary
import org.pnfolder.pnlibrary.api.plugin.ModuleContext
import org.pnfolder.pnlibrary.api.plugin.control.ModuleControl
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import java.util.concurrent.CompletionStage

/**
 * `/pn modules`: the modules of every plugin and their switches.
 *
 * | Command | What it does |
 * |---|---|
 * | `/pn modules` | every module with its state |
 * | `/pn modules plugins` | every plugin with its modules and the number of libraries |
 * | `/pn modules libs [<плагин>\|<плагин:модуль>]` | libraries with name, description, who registered them and version conflicts |
 * | `/pn modules lib enable\|disable\|retry <плагин\|плагин:модуль> <group:artifact:version>` | switches one library: on and off (saved across restarts), or tries again after an error |
 * | `/pn modules mc force <модуль> on\|off` | start the module although the server's Minecraft version is outside what it declared; saved |
 * | `/pn modules disk` | what every plugin takes on the disk: jar, settings, libraries, logs, data |
 * | `/pn modules view <модуль>` | one module: state, last error, whether it starts with the server |
 * | `/pn modules enable\|disable\|restart\|reload <модуль>` | switches the module now |
 * | `/pn modules auto <модуль> on\|off` | whether the module starts with the server; saved |
 *
 * `<модуль>` is `плагин:модуль`, or just the module id when it is unique. Requires `pnlibrary.modules`; the console
 * always may use it. A module its plugin registered without hooks is listed but cannot be switched.
 */
class ModulesCommand(private val library: PnLibrary) {
    private val libraries = LibraryPages(library)
    fun definition(): CommandDefinition = command("modules") {
        permission(PERMISSION)
        noPermissionMessage("Модули плагинов доступны администраторам. Нужно право $PERMISSION.")
        consoleBypassesPermission()
        executes { list(it) }
        literal("libs") {
            executes { libraries.libs(it, null) }
            argument("owner", ArgumentType.string()) {
                suggests { LibraryRow.all(library).map { it.owner }.distinct() }
                executes { libraries.libs(it, it.get<String>("owner")) }
            }
        }
        literal("lib") {
            for (action in listOf("enable", "disable", "retry")) {
                literal(action) {
                    argument("owner", ArgumentType.string()) {
                        suggests { LibraryRow.all(library).map { it.owner }.distinct() }
                        argument("library", ArgumentType.string()) {
                            suggests { LibraryRow.all(library).map { it.coordinates }.distinct() }
                            executes { libraries.changeLibrary(it, action, it.get<String>("owner"), it.get<String>("library")) }
                        }
                    }
                }
            }
        }
        literal("mc") {
            literal("force") {
                argument("module", ArgumentType.string()) {
                    suggests { names() }
                    for ((word, value) in listOf("on" to true, "off" to false)) {
                        literal(word) {
                            executes { context ->
                                withModule(context, context.get<String>("module")) { kit, name, module ->
                                    forceMinecraft(context, kit, name, module, value)
                                }
                            }
                        }
                    }
                }
            }
        }
        literal("plugins") { executes { plugins(it) } }
        literal("disk") { executes { disk(it) } }
        literal("view") { moduleArgument { context, typed -> view(context, typed) } }
        for (action in Action.values()) {
            literal(action.word) { moduleArgument { context, typed -> change(context, typed, action) } }
        }
        literal("auto") {
            argument("module", ArgumentType.string()) {
                suggests { names() }
                for ((word, value) in listOf("on" to true, "off" to false)) {
                    literal(word) {
                        executes { context ->
                            withModule(context, context.get<String>("module")) { kit, name, module -> auto(context, kit, name, module, value) }
                        }
                    }
                }
            }
        }
    }

    private fun CommandNodeBuilder.moduleArgument(run: (CommandContext, String) -> Unit) {
        argument("module", ArgumentType.string()) {
            suggests { names() }
            executes { run(it, it.get<String>("module")) }
        }
    }

    private fun names(): List<String> = ModuleLabels.named(library).map { it.first }

    private fun kit(context: CommandContext) = ChatKit(ChatReader.of(context.sender))

    private fun withModule(context: CommandContext, typed: String, action: (ChatKit, String, ModuleContext) -> Unit) {
        val kit = kit(context)
        val all = ModuleLabels.named(library)
        val found = all.firstOrNull { it.first.equals(typed, true) }
            ?: all.singleOrNull { it.second.id.value.equals(typed, true) }
        if (found == null) {
            context.sender.send(kit.text("Такого модуля нет или имя неоднозначно: $typed. Список: $ROOT", ChatPalette.DANGER))
            return
        }
        action(kit, found.first, found.second)
    }

    private fun list(context: CommandContext) {
        val kit = kit(context)
        val all = ModuleLabels.named(library)
        val lines = mutableListOf(kit.blank(), kit.header("Модули плагинов", kit.text("${all.size}", ChatPalette.LABEL)))
        if (all.isEmpty()) lines += kit.note("Модулей пока нет.", ChatPalette.FAINT)
        all.forEach { (name, module) ->
            val control = module.control
            val state = control.state
            var row = kit.text(" ").append(kit.text(kit.glyph('●'), ModuleLabels.color(state)))
                .append(kit.text(" ${name}", ChatPalette.ACCENT))
                .append(kit.text("  ${ModuleLabels.label(state)}", ModuleLabels.color(state)))
            if (!control.isControllable) {
                row = row.append(kit.dot()).append(kit.text("без переключателя", ChatPalette.FAINT))
            } else if (!control.autoStart) {
                row = row.append(kit.dot()).append(kit.text("не включается сам", ChatPalette.WARN))
            }
            lines += if (kit.reader.interactive) row.clickEvent(ClickEvent.runCommand("$ROOT view ${name}")) else row
        }
        if (!kit.reader.interactive) {
            lines += kit.blank()
            lines += kit.commandHints(
                "$ROOT view <модуль>" to "карточка модуля",
                "$ROOT enable|disable|restart|reload <модуль>" to "переключить сейчас",
                "$ROOT auto <модуль> on|off" to "включать ли вместе с сервером",
            )
        }
        lines.forEach(context.sender::send)
    }

    private fun libraryLine(kit: ChatKit, library: ModuleLibrary, fromPlugin: Boolean): net.kyori.adventure.text.Component {
        val suffix = if (fromPlugin) " (от плагина)" else ""
        val line = kit.text("    ${library.title} ${library.version}$suffix", if (fromPlugin) ChatPalette.FAINT else ChatPalette.SOFT)
        return library.description?.let { line.append(kit.text(": $it", ChatPalette.LABEL)) } ?: line
    }

    private fun forceMinecraft(context: CommandContext, kit: ChatKit, name: String, module: ModuleContext, on: Boolean) {
        val target = module as? org.pnfolder.pnlibrary.core.plugin.module.MinecraftForcible
        if (target == null || module.minecraft.declared == null) {
            context.sender.send(kit.note("У $name не объявлены версии Minecraft или он не из этого сервера.", ChatPalette.WARN))
            return
        }
        target.forceMinecraft(on)
        val text = if (on) "запускать при любой версии Minecraft (на свой риск)" else "версия Minecraft снова проверяется"
        context.sender.send(kit.text("$name: ", ChatPalette.TEXT).append(kit.text(text, ChatPalette.SOFT)))
    }

    private fun disk(context: CommandContext) {
        val kit = kit(context)
        val report = DiskUsage.scan(DiskUsage.pluginsFolder, DiskUsage.registeredFiles(library))
        val lines = mutableListOf(kit.blank(), kit.header("Диск плагинов", kit.text(DiskUsage.format(report.total), ChatPalette.LABEL)))
        report.rows.forEach { row ->
            val share = if (report.total > 0) row.total * 100 / report.total else 0
            lines += kit.text(" ${row.name}", ChatPalette.ACCENT).append(kit.dot())
                .append(kit.text("${DiskUsage.format(row.total)} ($share%)", if (share >= 50) ChatPalette.WARN else ChatPalette.SOFT))
            lines += kit.text(
                "    плагин ${DiskUsage.format(row.jar)} · настройки ${DiskUsage.format(row.configs)} · библиотеки ${DiskUsage.format(row.libraries)}" +
                    " · журналы ${DiskUsage.format(row.logs)} · данные ${DiskUsage.format(row.data)}",
                ChatPalette.FAINT,
            )
        }
        lines += kit.blank()
        lines += kit.hoverField("Библиотеки pnLibrary", kit.text(DiskUsage.format(report.registeredLibraries), ChatPalette.TEXT))
        lines += kit.hoverField("Свободно на диске", kit.text("${DiskUsage.format(report.free)} из ${DiskUsage.format(report.capacity)}", ChatPalette.TEXT))
        lines.forEach(context.sender::send)
    }

    private fun plugins(context: CommandContext) {
        val kit = kit(context)
        val all = library.plugins.all()
        val lines = mutableListOf(kit.blank(), kit.header("Плагины библиотеки", kit.text("${all.size}", ChatPalette.LABEL)))
        all.sortedBy { it.id.value }.forEach { plugin ->
            val modules = plugin.all()
            lines += kit.text(" ${plugin.id.value}", ChatPalette.ACCENT)
                .append(kit.dot()).append(kit.text("модулей ${modules.size}", ChatPalette.SOFT))
                .append(kit.dot()).append(kit.text("библиотек ${plugin.allLibraries.size}", ChatPalette.SOFT))
            modules.forEach { module ->
                val state = module.control.state
                lines += kit.text("    ${module.id.value}", ChatPalette.TEXT).append(kit.text("  ${ModuleLabels.label(state)}", ModuleLabels.color(state)))
            }
        }
        lines.forEach(context.sender::send)
    }

    private fun view(context: CommandContext, typed: String) = withModule(context, typed) { kit, name, module ->
        val control = module.control
        val lines = mutableListOf(
            kit.blank(),
            kit.header(ModuleLabels.title(module), kit.bracketed(ModuleLabels.label(control.state), ModuleLabels.color(control.state))),
            kit.hoverField("Имя", kit.text(name, ChatPalette.TEXT)),
            kit.hoverField("Описание", kit.text(module.metadata.description ?: "плагин не описал", ChatPalette.TEXT)),
            kit.hoverField("Переключается", kit.text(if (control.isControllable) "да" else "нет, плагин не дал методы включения", ChatPalette.TEXT)),
            kit.hoverField("Включается с сервером", kit.text(if (control.autoStart) "да" else "нет", ChatPalette.TEXT)),
        )
        val own = module.libraries.loaded
        val shared = library.plugins.all().firstOrNull { plugin -> plugin.all().any { it === module } }?.libraries?.loaded.orEmpty()
        if (own.isNotEmpty() || shared.isNotEmpty()) {
            lines += kit.hoverField("Библиотеки", kit.text("${own.size + shared.size}", ChatPalette.TEXT))
            own.forEach { lines += libraryLine(kit, it, false) }
            shared.forEach { lines += libraryLine(kit, it, true) }
        }
        module.minecraft.declared?.let { declared ->
            val check = module.minecraft
            val color = if (check.blocked) ChatPalette.DANGER else if (check.note != null) ChatPalette.WARN else ChatPalette.OK
            lines += kit.hoverField("Minecraft", kit.text("$declared, сервер ${check.server ?: "неизвестно"}", color))
            check.note?.let { lines += kit.text("    $it", ChatPalette.LABEL) }
            if (check.forced) lines += kit.text("    администратор разрешил запуск на любой версии", ChatPalette.WARN)
        }
        control.lastError?.let { lines += kit.hoverField("Ошибка", kit.text(it.message ?: it.javaClass.simpleName, ChatPalette.DANGER)) }
        if (control.isControllable && kit.reader.interactive) {
            var buttons = kit.text(" ")
            Action.values().forEach { a ->
                buttons = buttons.append(kit.option(a.label, "$ROOT ${a.word} ${name}", a.hint, a.color)).append(kit.text(" "))
            }
            val toggle = if (control.autoStart) "off" to "Не включать с сервером" else "on" to "Включать с сервером"
            buttons = buttons.append(kit.option(toggle.second, "$ROOT auto ${name} ${toggle.first}", "Сохраняется между перезапусками", ChatPalette.SOFT))
            lines += kit.blank()
            lines += buttons
        }
        lines.forEach(context.sender::send)
    }

    private fun change(context: CommandContext, typed: String, action: Action) = withModule(context, typed) { kit, name, module ->
        val control = module.control
        if (!control.isControllable) {
            context.sender.send(kit.text("У модуля ${name} нет методов включения и выключения.", ChatPalette.DANGER))
            return@withModule
        }
        action.run(control).whenComplete { _, _ ->
            val failed = control.lastError
            context.sender.send(
                if (failed != null) {
                    kit.text("Модуль ${name}: ошибка, ${failed.message ?: failed.javaClass.simpleName}", ChatPalette.DANGER)
                } else {
                    kit.text("Модуль ${name}: ${ModuleLabels.label(control.state)}", ChatPalette.OK)
                },
            )
        }
    }

    private fun auto(context: CommandContext, kit: ChatKit, name: String, module: ModuleContext, enabled: Boolean) {
        if (!module.control.isControllable) {
            context.sender.send(kit.text("У модуля ${name} нет методов включения и выключения.", ChatPalette.DANGER))
            return
        }
        module.control.setAutoStart(enabled)
        val text = if (enabled) "будет включаться вместе с сервером" else "не будет включаться вместе с сервером"
        context.sender.send(kit.text("Модуль ${name} $text.", ChatPalette.OK))
    }

    private enum class Action(
        val word: String,
        val label: String,
        val hint: String,
        val color: TextColor,
        val run: (ModuleControl) -> CompletionStage<*>,
    ) {
        ENABLE("enable", "Включить", "Запустить модуль", ChatPalette.OK, { it.enable() }),
        DISABLE("disable", "Выключить", "Остановить до следующего запуска сервера", ChatPalette.DANGER, { it.disable() }),
        RESTART("restart", "Перезапустить", "Выключить и включить заново", ChatPalette.WARN, { it.restart() }),
        RELOAD("reload", "Перечитать", "Перечитать настройки модуля", ChatPalette.INFO, { it.reload() }),
    }

    companion object {
        const val PERMISSION = "pnlibrary.modules"
        const val ROOT = "/pn modules"
    }
}
