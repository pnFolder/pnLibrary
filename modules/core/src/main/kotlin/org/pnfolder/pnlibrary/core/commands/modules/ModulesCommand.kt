package org.pnfolder.pnlibrary.core.commands.modules

import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.TextColor
import org.pnfolder.pnlibrary.api.chat.ChatKit
import org.pnfolder.pnlibrary.api.chat.ChatReader
import org.pnfolder.pnlibrary.api.chat.style.ChatPalette
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.commands.definition.ArgumentType
import org.pnfolder.pnlibrary.api.commands.definition.CommandNodeBuilder
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition
import org.pnfolder.pnlibrary.api.commands.definition.command
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
 * | `/pn modules view <модуль>` | one module: state, last error, whether it starts with the server |
 * | `/pn modules enable\|disable\|restart\|reload <модуль>` | switches the module now |
 * | `/pn modules auto <модуль> on\|off` | whether the module starts with the server; saved |
 *
 * `<модуль>` is `плагин:модуль`, or just the module id when it is unique. Requires `pnlibrary.modules`; the console
 * always may use it. A module its plugin registered without hooks is listed but cannot be switched.
 */
class ModulesCommand(private val library: PnLibrary) {
    fun definition(): CommandDefinition = command("modules") {
        permission(PERMISSION)
        noPermissionMessage("Модули плагинов доступны администраторам. Нужно право $PERMISSION.")
        consoleBypassesPermission()
        executes { list(it) }
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

    private fun view(context: CommandContext, typed: String) = withModule(context, typed) { kit, name, module ->
        val control = module.control
        val lines = mutableListOf(
            kit.blank(),
            kit.header(ModuleLabels.title(module), kit.bracketed(ModuleLabels.label(control.state), ModuleLabels.color(control.state))),
            kit.hoverField("Имя", kit.text(name, ChatPalette.TEXT)),
            kit.hoverField("Переключается", kit.text(if (control.isControllable) "да" else "нет, плагин не дал методы включения", ChatPalette.TEXT)),
            kit.hoverField("Включается с сервером", kit.text(if (control.autoStart) "да" else "нет", ChatPalette.TEXT)),
        )
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
