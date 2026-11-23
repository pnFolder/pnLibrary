package org.pnfolder.pnlibrary.core.placeholders

import net.kyori.adventure.text.Component
import org.pnfolder.pnlibrary.api.chat.ChatPalette
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.commands.definition.ArgumentType
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition
import org.pnfolder.pnlibrary.api.commands.definition.command
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderInfo
import org.pnfolder.pnlibrary.api.placeholders.ViewerInfo
import org.pnfolder.pnlibrary.api.placeholders.Visibility
import org.pnfolder.pnlibrary.api.player.LibraryPlayer
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * `/pn placeholders [page]` lists every placeholder with its type, default format and what is shown to
 * PlaceholderAPI; `/pn placeholders formats` lists the formats; `/pn placeholders test <address> [player uuid]`
 * works one out by hand. Requires `pnlibrary.placeholders`; the console always may.
 */
fun placeholdersCommand(engine: PlaceholderEngine): CommandDefinition = command("placeholders") {
    permission(PERMISSION)
    noPermissionMessage("Плейсхолдеры доступны администраторам. Нужно право $PERMISSION.")
    consoleBypassesPermission()
    executes { list(engine, it, 1) }
    argument("page", ArgumentType.integer()) { executes { list(engine, it, it.get<Int>("page")) } }
    literal("info") {
        argument("address", ArgumentType.string()) { executes { info(engine, it, it.get<String>("address")) } }
    }
    literal("formats") {
        executes { it.sender.send(Component.text("Форматы: " + engine.formatNames().joinToString(", "), ChatPalette.TEXT)) }
    }
    literal("test") {
        argument("address", ArgumentType.string()) {
            executes { test(engine, it, it.get<String>("address"), null) }
            argument("player", ArgumentType.string()) { executes { test(engine, it, it.get<String>("address"), it.get<String>("player")) } }
        }
    }
}

private const val PERMISSION = "pnlibrary.placeholders"
private const val PAGE_SIZE = 12
private const val TEST_TIMEOUT_SECONDS = 3L

private fun list(engine: PlaceholderEngine, context: CommandContext, requested: Int) {
    val all = engine.list()
    val pages = ((all.size + PAGE_SIZE - 1) / PAGE_SIZE).coerceAtLeast(1)
    val page = requested.coerceIn(1, pages)
    context.sender.send(Component.text("Плейсхолдеры: ${all.size}, страница $page/$pages", ChatPalette.ACCENT))
    all.drop((page - 1) * PAGE_SIZE).take(PAGE_SIZE).forEach { context.sender.send(row(it)) }
    context.sender.send(
        Component.text(
            "Как писать: /pn placeholders info <адрес>; проверить: /pn placeholders test <адрес> [uuid]; форматы: /pn placeholders formats",
            ChatPalette.FAINT,
        ),
    )
}

/** One line of the placeholder list. */
fun row(info: PlaceholderInfo): Component {
    val format = info.format?.let { " |$it" }.orEmpty()
    val outside = if (info.published.isEmpty()) "" else "  PlaceholderAPI: " + info.published.joinToString { "%$it%" }
    val scope = if (info.visibility == Visibility.ALL) "" else "  [${info.visibility.name.lowercase()}]"
    val about = if (info.description.isEmpty()) "" else "  ${info.description}"
    return Component.text("{${info.address}}", ChatPalette.TEXT)
        .append(Component.text("  ${info.type}$format  ${info.owner}$scope$outside$about", ChatPalette.SOFT))
}

/** One way to write an address: a short [label], the [text] to put into a message and what it means. */
data class UsageVariant(val label: String, val text: String, val meaning: String)

/**
 * The ways [info] can be written, nearest first. Only the forms that can work for its visibility are listed:
 * a placeholder of `MODULE` is reachable by its own module alone, one of `PLUGIN` by the modules of its plugin.
 */
fun usageVariants(info: PlaceholderInfo): List<UsageVariant> {
    val address = info.address
    val plugin = info.owner.substringBefore('/')
    val shared = info.visibility != Visibility.MODULE
    return buildList {
        add(UsageVariant("Как есть", "{$address}", "ближайший: свой модуль, свой плагин, потом остальные"))
        add(UsageVariant("Этот модуль", "{${info.owner}@$address}", "именно модуль ${info.owner}"))
        if (shared) add(UsageVariant("Весь плагин", "{$plugin@$address}", "любой модуль плагина $plugin"))
        add(UsageVariant("Мой модуль", "{~~@$address}", "только мой модуль: работает внутри ${info.owner}"))
        if (shared) add(UsageVariant("Мой плагин", "{~@$address}", "только мой плагин: работает внутри любого модуля плагина $plugin"))
        add(UsageVariant("С форматом", "{$address|${info.format ?: "имя"}}", "формат после чёрточки; список: /pn placeholders formats"))
    }
}

private fun info(engine: PlaceholderEngine, context: CommandContext, written: String) {
    val wanted = written.trim().removePrefix("{").removeSuffix("}").lowercase()
    val found = engine.list().filter { it.address.lowercase() == wanted }
    if (found.isEmpty()) {
        context.sender.send(Component.text("{$wanted}: такого плейсхолдера нет. Список: /pn placeholders", ChatPalette.WARN))
        return
    }
    found.forEach { entry ->
        context.sender.send(row(entry))
        usageVariants(entry).forEach { variant ->
            context.sender.send(
                Component.text("  ${variant.label}: ", ChatPalette.SOFT)
                    .append(Component.text(variant.text, ChatPalette.TEXT))
                    .append(Component.text("  ${variant.meaning}", ChatPalette.FAINT)),
            )
        }
    }
}

private fun test(engine: PlaceholderEngine, context: CommandContext, address: String, player: String?) {
    val expression = address.removePrefix("{").removeSuffix("}")
    val playerId = player?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: (context.sender as? LibraryPlayer)?.uniqueId
    val result = try {
        engine.resolveAll(expression, ViewerInfo.of(playerId)).toCompletableFuture().get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    } catch (_: TimeoutException) {
        context.sender.send(Component.text("{$expression}: нет ответа за $TEST_TIMEOUT_SECONDS с", ChatPalette.WARN))
        return
    } catch (error: Exception) {
        context.sender.send(Component.text("{$expression}: ${error.cause?.message ?: error.message}", ChatPalette.DANGER))
        return
    }
    val line = if (result == null) "не найден или нет значения" else result.toString()
    val color = if (result == null) ChatPalette.WARN else ChatPalette.OK
    context.sender.send(Component.text("{$expression} = ", ChatPalette.SOFT).append(Component.text(line, color)))
}
