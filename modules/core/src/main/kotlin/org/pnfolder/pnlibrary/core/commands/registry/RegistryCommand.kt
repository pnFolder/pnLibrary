package org.pnfolder.pnlibrary.core.commands.registry

import net.kyori.adventure.text.Component
import org.pnfolder.pnlibrary.api.chat.ChatPalette
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.commands.definition.ArgumentType
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition
import org.pnfolder.pnlibrary.api.commands.definition.command
import org.pnfolder.pnlibrary.api.registry.Registry
import org.pnfolder.pnlibrary.api.registry.RegistryEntry
import org.pnfolder.pnlibrary.api.registry.Visibility

private const val PERMISSION = "pnlibrary.registry"
private const val PAGE_SIZE = 12

/**
 * `/pn registry` shows what plugins registered, one way for every kind: `/pn registry` lists the kinds,
 * `/pn registry <kind> [page]` the entries with owner and visibility, `/pn registry find <text>` searches all kinds.
 * Requires `pnlibrary.registry`; the console always may.
 */
internal fun registryCommand(registries: List<Registry<*>>): CommandDefinition = command("registry") {
    permission(PERMISSION)
    noPermissionMessage("Реестры доступны администраторам. Нужно право $PERMISSION.")
    consoleBypassesPermission()
    executes { kinds(registries, it) }
    literal("find") {
        argument("text", ArgumentType.string()) { executes { find(registries, it, it.get<String>("text")) } }
    }
    argument("kind", ArgumentType.string()) {
        executes { show(registries, it, it.get<String>("kind"), 1) }
        argument("page", ArgumentType.integer()) { executes { show(registries, it, it.get<String>("kind"), it.get<Int>("page")) } }
    }
}

private fun kinds(registries: List<Registry<*>>, context: CommandContext) {
    context.sender.sendMessage(Component.text("Реестры:", ChatPalette.ACCENT))
    registries.forEach { context.sender.sendMessage(Component.text("  ${it.name}  ${it.entries().size}", ChatPalette.TEXT)) }
    context.sender.sendMessage(Component.text("Смотреть: /pn registry <имя> [страница]; искать: /pn registry find <текст>", ChatPalette.FAINT))
}

private fun show(registries: List<Registry<*>>, context: CommandContext, kind: String, requested: Int) {
    val registry = registries.firstOrNull { it.name.equals(kind, ignoreCase = true) }
    if (registry == null) {
        context.sender.sendMessage(Component.text("Нет реестра «$kind». Есть: ${registries.joinToString { it.name }}", ChatPalette.TEXT))
        return
    }
    val entries = registry.entries()
    val pages = ((entries.size + PAGE_SIZE - 1) / PAGE_SIZE).coerceAtLeast(1)
    val page = requested.coerceIn(1, pages)
    context.sender.sendMessage(Component.text("${registry.name}: ${entries.size}, страница $page/$pages", ChatPalette.ACCENT))
    entries.drop((page - 1) * PAGE_SIZE).take(PAGE_SIZE).forEach { context.sender.sendMessage(row(it)) }
}

private fun find(registries: List<Registry<*>>, context: CommandContext, text: String) {
    val found = registries.flatMap { registry ->
        registry.entries().filter { text.lowercase() in it.key.lowercase() || text.lowercase() in it.owner.lowercase() }
            .map { registry.name to it }
    }
    context.sender.sendMessage(Component.text("Найдено: ${found.size}", ChatPalette.ACCENT))
    found.take(PAGE_SIZE * 2).forEach { (kind, entry) -> context.sender.sendMessage(row(entry, kind)) }
}

internal fun row(entry: RegistryEntry<*>, kind: String? = null): Component {
    val scope = if (entry.visibility == Visibility.ALL) "" else "  [${entry.visibility.name.lowercase()}]"
    val about = if (entry.description.isEmpty()) "" else "  ${entry.description}"
    val prefix = kind?.let { "$it  " }.orEmpty()
    return Component.text(prefix + entry.key, ChatPalette.TEXT)
        .append(Component.text("  ${entry.owner}$scope$about", ChatPalette.SOFT))
}
