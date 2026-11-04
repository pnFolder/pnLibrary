package org.pnfolder.pnlibrary.core.placeholders

import net.kyori.adventure.text.Component
import org.pnfolder.pnlibrary.api.audiences.LibraryPlayer
import org.pnfolder.pnlibrary.api.chat.ChatPalette
import org.pnfolder.pnlibrary.api.commands.ArgumentType
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.commands.CommandDefinition
import org.pnfolder.pnlibrary.api.commands.command
import org.pnfolder.pnlibrary.api.plugin.PluginId
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * `/pn placeholders [page]` lists every placeholder with its type, default format and what is published to
 * PlaceholderAPI; `/pn placeholders test <address> [player uuid]` works one out by hand.
 * Requires `pnlibrary.placeholders`; the console always may.
 */
internal fun placeholdersCommand(hub: PlaceholderHub): CommandDefinition = command("placeholders") {
    permission(PERMISSION)
    noPermissionMessage("Плейсхолдеры доступны администраторам. Нужно право $PERMISSION.")
    consoleBypassesPermission()
    executes { list(hub, it, 1) }
    argument("page", ArgumentType.integer()) { executes { list(hub, it, it.get<Int>("page")) } }
    literal("test") {
        argument("address", ArgumentType.string()) {
            executes { test(hub, it, it.get<String>("address"), null) }
            argument("player", ArgumentType.string()) { executes { test(hub, it, it.get<String>("address"), it.get<String>("player")) } }
        }
    }
}

private const val PERMISSION = "pnlibrary.placeholders"
private const val PAGE_SIZE = 12
private const val TEST_TIMEOUT_SECONDS = 3L

private fun list(hub: PlaceholderHub, context: CommandContext, requested: Int) {
    val all = hub.describe()
    val pages = ((all.size + PAGE_SIZE - 1) / PAGE_SIZE).coerceAtLeast(1)
    val page = requested.coerceIn(1, pages)
    context.sender.send(Component.text("Плейсхолдеры: ${all.size}, страница $page/$pages", ChatPalette.ACCENT))
    all.drop((page - 1) * PAGE_SIZE).take(PAGE_SIZE).forEach { context.sender.send(row(it)) }
    context.sender.send(Component.text("Проверить: /pn placeholders test <адрес> [uuid игрока]", ChatPalette.FAINT))
}

internal fun row(info: PlaceholderInfo): Component {
    val color = if (info.enabled) ChatPalette.TEXT else ChatPalette.FAINT
    val format = info.format?.let { " |$it" }.orEmpty()
    val outside = if (info.published.isEmpty()) "" else "  PlaceholderAPI: " + info.published.joinToString { "%$it%" }
    return Component.text("{${info.address}}", color)
        .append(Component.text("  ${info.type}$format$outside", ChatPalette.SOFT))
}

private fun test(hub: PlaceholderHub, context: CommandContext, address: String, player: String?) {
    val expression = address.removePrefix("{").removeSuffix("}")
    val playerId = player?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: (context.sender as? LibraryPlayer)?.uniqueId
    val service = hub.scope(PluginId.of("pnlibrary"), true)
    val result = try {
        service.resolve(expression, playerId).toCompletableFuture().get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
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
