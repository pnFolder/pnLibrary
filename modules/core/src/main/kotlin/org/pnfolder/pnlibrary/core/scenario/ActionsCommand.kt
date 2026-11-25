package org.pnfolder.pnlibrary.core.scenario

import net.kyori.adventure.text.Component
import org.pnfolder.pnlibrary.api.chat.ChatPalette
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.commands.definition.ArgumentType
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition
import org.pnfolder.pnlibrary.api.commands.definition.command
import org.pnfolder.pnlibrary.core.placeholders.UsageVariant

/**
 * `/pn actions [page]` lists the named actions, requirements and functions the plugins defined, with owner and
 * visibility; `/pn actions info <name>` shows how each one is written. Requires `pnlibrary.actions`; the console always may.
 */
fun actionsCommand(source: () -> List<ScenarioDefinitionInfo>): CommandDefinition = command("actions") {
    permission(ACTIONS_PERMISSION)
    noPermissionMessage("Действия доступны администраторам. Нужно право $ACTIONS_PERMISSION.")
    consoleBypassesPermission()
    executes { list(source(), it, 1) }
    argument("page", ArgumentType.integer()) { executes { list(source(), it, it.get<Int>("page")) } }
    literal("info") {
        argument("name", ArgumentType.string()) { executes { info(source(), it, it.get<String>("name")) } }
    }
}

private const val ACTIONS_PERMISSION = "pnlibrary.actions"
private const val PAGE_SIZE = 12

private fun list(all: List<ScenarioDefinitionInfo>, context: CommandContext, requested: Int) {
    val pages = ((all.size + PAGE_SIZE - 1) / PAGE_SIZE).coerceAtLeast(1)
    val page = requested.coerceIn(1, pages)
    context.sender.sendMessage(Component.text("Действия, требования и функции: ${all.size}, страница $page/$pages", ChatPalette.ACCENT))
    all.drop((page - 1) * PAGE_SIZE).take(PAGE_SIZE).forEach { context.sender.sendMessage(definitionRow(it)) }
    context.sender.sendMessage(Component.text("Как писать: /pn actions info <имя>", ChatPalette.FAINT))
}

/** One line of the list: the name, what it is, who owns it and who may use it. */
fun definitionRow(info: ScenarioDefinitionInfo): Component {
    val scope = if (info.visibility == "all") "" else "  [${info.visibility}]"
    return Component.text(info.name, ChatPalette.TEXT)
        .append(Component.text("  ${kindTitle(info.kind)}  ${info.owner}$scope", ChatPalette.SOFT))
}

private fun kindTitle(kind: String) = when (kind) {
    "action" -> "действие"
    "requirement" -> "требование"
    else -> "функция"
}

/**
 * How [info] is written in a configuration or an expression, nearest first. A definition of `module` visibility is
 * reachable by its own module only, one of `plugin` visibility by the modules of its plugin.
 */
fun definitionUsage(info: ScenarioDefinitionInfo): List<UsageVariant> {
    val plugin = info.owner.substringBefore('/')
    val shared = info.visibility != "module"
    fun written(owner: String?) = when (info.kind) {
        "action" -> "{ type: call, name: '${owner?.let { "$it@" }.orEmpty()}${info.name}' }"
        "requirement" -> "{ type: ref, name: '${owner?.let { "$it@" }.orEmpty()}${info.name}' }"
        else -> "${owner?.let { "$it@" }.orEmpty()}${info.name}()"
    }
    return buildList {
        add(UsageVariant("Как есть", written(null), "ближайший: свой модуль, свой плагин, потом остальные"))
        add(UsageVariant("Этот модуль", written(info.owner), "именно модуль ${info.owner}"))
        if (shared) add(UsageVariant("Весь плагин", written(plugin), "любой модуль плагина $plugin"))
        add(UsageVariant("Мой модуль", written("~~"), "только мой модуль: работает внутри ${info.owner}"))
        if (shared) add(UsageVariant("Мой плагин", written("~"), "только мой плагин: работает внутри любого модуля плагина $plugin"))
    }
}

private fun info(all: List<ScenarioDefinitionInfo>, context: CommandContext, written: String) {
    val wanted = written.trim().lowercase().removeSuffix("()")
    val found = all.filter { it.name == wanted }
    if (found.isEmpty()) {
        context.sender.sendMessage(Component.text("$wanted: такого действия, требования или функции нет. Список: /pn actions", ChatPalette.WARN))
        return
    }
    found.forEach { entry ->
        context.sender.sendMessage(definitionRow(entry))
        definitionUsage(entry).forEach { variant ->
            context.sender.sendMessage(
                Component.text("  ${variant.label}: ", ChatPalette.SOFT)
                    .append(Component.text(variant.text, ChatPalette.TEXT))
                    .append(Component.text("  ${variant.meaning}", ChatPalette.FAINT)),
            )
        }
    }
}
