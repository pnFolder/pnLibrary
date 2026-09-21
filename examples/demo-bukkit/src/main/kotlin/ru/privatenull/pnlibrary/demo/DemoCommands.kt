package ru.privatenull.pnlibrary.demo

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import ru.privatenull.pnlibrary.api.actions.ActionTarget
import ru.privatenull.pnlibrary.api.actions.MessageAction
import ru.privatenull.pnlibrary.api.commands.ArgumentType
import ru.privatenull.pnlibrary.api.commands.CommandRegistration
import ru.privatenull.pnlibrary.api.commands.command
import ru.privatenull.pnlibrary.api.currency.Currency
import ru.privatenull.pnlibrary.api.plugin.PluginContext
import ru.privatenull.pnlibrary.api.runtime.PnLibrary
import java.math.BigDecimal
import java.time.Duration
import java.util.UUID

object DemoCommands {
    fun registerPortable(plugin: DemoPlugin, library: PnLibrary, context: PluginContext, currency: Currency): CommandRegistration =
        library.commands.register(plugin, command("pndemo-lib") {
            aliases("pndemoapi"); permission("pndemo.use")
            literal("status") { executes { it.sender.send(Component.text("pnLibrary command API: ${context.id}, runtime ${library.version}")) } }
            literal("give") { argument("amount", ArgumentType.decimal()) { executes { invocation ->
                val id = runCatching { UUID.fromString(invocation.sender.id) }.getOrNull()
                if (id == null) invocation.sender.send(Component.text("This action requires a player sender."))
                else currency.deposit(id, invocation.get<BigDecimal>("amount")).thenAccept { result ->
                    invocation.sender.send(Component.text("${result.status}: ${currency.format(result.currentBalance ?: BigDecimal.ZERO)}"))
                }
            } } }
        })

    class Native(private val plugin: DemoPlugin, private val context: PluginContext, private val currency: Currency,
                 private val state: DemoState) : CommandExecutor, TabCompleter {
        override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
            if (args.isEmpty() || args[0].equals("status", true)) {
                DemoEvent().callEvent()
                if (sender is Player) {
                    PnLibraryProviderFacade.sendAction(plugin, sender)
                    DemoActionShowcase.run(plugin, sender, mapOf("sender" to sender.name))
                }
                sender.sendMessage("§8[§bpnDemo§8] §7API §f1 §7· context §f${context.id} §7· online §f${Bukkit.getOnlinePlayers().size}")
                sender.sendMessage("§7Используются: lifecycle, metrics, diagnostics, tasks, currency, placeholders, localization")
                sender.sendMessage(context.components.serialize(context.components.deserialize("<aqua>components + services + commands are active")))
                return true
            }
            if (sender !is Player) { sender.sendMessage("Only players can use this demo action."); return true }
            when (args[0].lowercase()) {
                "balance" -> currency.balance(sender.uniqueId).thenAccept { sender.sendMessage("§bБаланс: §f${currency.format(it)}") }
                "give" -> {
                    val cooldown = context.cooldowns.acquire(sender.uniqueId, "give", Duration.ofSeconds(2))
                    if (!cooldown.allowed) sender.sendMessage("§eПодождите ${cooldown.remaining.toMillis()} мс.")
                    else args.getOrNull(1)?.toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO }?.let {
                        currency.deposit(sender.uniqueId, it).thenAccept { result -> sender.sendMessage("§a${result.status}: §f${currency.format(result.currentBalance ?: BigDecimal.ZERO)}") }
                    } ?: sender.sendMessage("§cИспользование: /pndemo give <amount>")
                }
                "reload" -> { plugin.reloadConfig(); sender.sendMessage("§aКонфигурация перезагружена.") }
                else -> sender.sendMessage("§7/pndemo status|balance|give <amount>|reload")
            }
            return true
        }
        override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> =
            if (args.size == 1) listOf("status", "balance", "give", "reload").filter { it.startsWith(args[0], true) } else emptyList()
    }
}

private object PnLibraryProviderFacade {
    fun sendAction(plugin: DemoPlugin, player: Player) {
        val audience = ru.privatenull.pnlibrary.api.runtime.PnLibraryProvider.get().audiences.player(player.uniqueId) ?: return
        plugin.context.actions.execute(audience, ru.privatenull.pnlibrary.api.runtime.PnLibraryProvider.get().audiences.all(),
            listOf(MessageAction(listOf("<aqua>pnDemo <white>uses the unified Action API"), target = ActionTarget.PLAYER)))
    }
}
