package org.pnfolder.pnlibrary.core.commands.cooldowns

import net.kyori.adventure.text.Component
import org.pnfolder.pnlibrary.api.chat.ChatPalette
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.commands.definition.ArgumentType
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition
import org.pnfolder.pnlibrary.api.commands.definition.command
import org.pnfolder.pnlibrary.api.plugin.PluginRegistry
import org.pnfolder.pnlibrary.api.audience.Audiences
import java.time.Duration
import java.util.UUID

private const val PERMISSION = "pnlibrary.cooldowns"

/**
 * `/pn cooldowns <player>` shows what holds an online player back, in every plugin and module; `/pn cooldowns <player> clear`
 * lets the player act again. Requires `pnlibrary.cooldowns`; the console always may.
 */
internal fun cooldownsCommand(plugins: () -> PluginRegistry, audiences: () -> Audiences): CommandDefinition = command("cooldowns") {
    permission(PERMISSION)
    noPermissionMessage("Кулдауны доступны администраторам. Нужно право $PERMISSION.")
    consoleBypassesPermission()
    executes { it.sender.sendMessage(Component.text("Кулдауны игрока: /pn cooldowns <игрок> [clear]", ChatPalette.FAINT)) }
    argument("player", ArgumentType.string()) {
        executes { show(plugins(), audiences(), it, it.get<String>("player")) }
        literal("clear") { executes { clear(plugins(), audiences(), it, it.get<String>("player")) } }
    }
}

/** One cooldown running now: who keeps it (`plugin/module`), its action and the time left. */
internal data class RunningCooldown(val owner: String, val action: String, val remaining: Duration)

internal fun runningCooldowns(plugins: PluginRegistry, subject: UUID): List<RunningCooldown> =
    plugins.all().flatMap { plugin ->
        plugin.all().flatMap { module ->
            module.cooldowns.active(subject).map { (action, left) -> RunningCooldown("${plugin.id.value}/${module.id.value}", action, left) }
        }
    }.sortedByDescending { it.remaining }

private fun show(plugins: PluginRegistry, audiences: Audiences, context: CommandContext, name: String) {
    val player = audiences.player(name) ?: return unknown(context, name)
    val running = runningCooldowns(plugins, player.uniqueId)
    context.sender.sendMessage(Component.text("Кулдауны ${name}: ${running.size}", ChatPalette.ACCENT))
    running.forEach {
        context.sender.sendMessage(
            Component.text("  ${it.action}", ChatPalette.TEXT).append(Component.text("  ${it.owner}  ${seconds(it.remaining)} с", ChatPalette.SOFT)),
        )
    }
}

private fun clear(plugins: PluginRegistry, audiences: Audiences, context: CommandContext, name: String) {
    val player = audiences.player(name) ?: return unknown(context, name)
    val removed = plugins.all().sumOf { plugin -> plugin.all().sumOf { it.cooldowns.clear(player.uniqueId) } }
    context.sender.sendMessage(Component.text("Сброшено кулдаунов у $name: $removed", ChatPalette.TEXT))
}

private fun unknown(context: CommandContext, name: String) {
    context.sender.sendMessage(Component.text("Игрока $name нет онлайн.", ChatPalette.TEXT))
}

private fun seconds(left: Duration): Long = (left.toMillis() + 999) / 1000
