package ru.privatenull.pnlibrary.demo

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.java.JavaPlugin
import ru.privatenull.pnlibrary.api.currency.Currency
import ru.privatenull.pnlibrary.api.currency.CurrencyAccount
import ru.privatenull.pnlibrary.api.currency.CurrencyRejectReason
import ru.privatenull.pnlibrary.api.currency.CurrencyResult
import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticContainer
import ru.privatenull.pnlibrary.api.events.Event
import ru.privatenull.pnlibrary.api.events.EventHandler as PnEventHandler
import ru.privatenull.pnlibrary.api.events.Listener as PnListener
import ru.privatenull.pnlibrary.api.placeholders.PlaceholderCachePolicy
import ru.privatenull.pnlibrary.api.placeholders.PlaceholderCacheScope
import ru.privatenull.pnlibrary.api.runtime.PnLibraryProvider
import ru.privatenull.pnlibrary.api.tasks.TaskSpec
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Supplier

/**
 * A deliberately small, production-shaped plugin that exercises pnLibrary end to end.
 * It is packaged separately so it can be dropped into a test server without changing the runtime.
 */
class DemoPlugin : JavaPlugin(), CommandExecutor, TabCompleter, Listener {
    private lateinit var context: ru.privatenull.pnlibrary.api.plugin.PluginContext
    private lateinit var currency: Currency
    private val balances = ConcurrentHashMap<UUID, BigDecimal>()
    private val joins = AtomicLong()
    private var pulse: AutoCloseable? = null

    override fun onEnable() {
        saveDefaultConfig()
        val library = PnLibraryProvider.getOrNull()
        if (library == null) {
            logger.severe("pnLibrary is not available; disabling pnLibraryDemo")
            server.pluginManager.disablePlugin(this)
            return
        }

        context = library.plugins.register(this) { builder ->
            builder.metrics(32592, true) { metrics ->
                metrics.simplePie("server_platform") { server.name }
                metrics.singleLineChart("demo_joins") { joins.get().toInt() }
            }
            builder.diagnostics(dataFolder.toPath(), DiagnosticContainer.builder("pndemo")
                .snapshot(Supplier {
                    mapOf("joins" to joins.get(), "online" to Bukkit.getOnlinePlayers().size,
                        "currencies" to library.currencyProviders.all().size)
                })
                .configuration("config.yml")
                .build())
            builder.listener(PnDemoEventListener(this))
        }

        currency = context.currencies.register("coins") { definition ->
            definition.descriptor { it.displayName("Demo Coins").symbol("◈").fractionDigits(2).roundingMode(RoundingMode.DOWN) }
            definition.operations { operations ->
                operations.balance { account -> balances[account.playerId] ?: BigDecimal.ZERO.setScale(2) }
                    .deposit { account, amount -> mutate(account.playerId, amount, true) }
                    .withdraw { account, amount -> mutate(account.playerId, amount, false) }
                    .setBalance { account, amount ->
                        val normalized = amount.setScale(2, RoundingMode.DOWN)
                        balances[account.playerId] = normalized
                        CurrencyResult.success(null, normalized)
                    }
                    .reset { account -> balances.remove(account.playerId); CurrencyResult.success() }
                    .format { amount -> "${amount.setScale(2, RoundingMode.DOWN)} ◈" }
            }
        }

        context.placeholders.placeholder("coins", String::class.java)
            .resolve { request -> currency.format(balances[request.requirePlayerId()] ?: BigDecimal.ZERO) }
            .fallback("0.00 ◈")
            .cache(PlaceholderCachePolicy(PlaceholderCacheScope.PLAYER, 2_000, 1_000))
            .publishToPlaceholderApi("pndemo", "coins")
            .register()

        pulse = context.tasks.schedule(TaskSpec.builder()
            .name("demo-pulse").key("demo-pulse").interval(java.time.Duration.ofSeconds(30))
            .action { context.logger.info("pulse: online=${Bukkit.getOnlinePlayers().size}, joins=${joins.get()}") }
            .build())
        getCommand("pndemo")?.setExecutor(this)
        getCommand("pndemo")?.tabCompleter = this
        server.pluginManager.registerEvents(this, this)
        context.lifecycle.enabled().ok("Context", context.id.value).ok("Currency", currency.key.toString())
            .ok("Placeholder", "pndemo_coins").ok("Tasks", "demo-pulse").show()
    }

    override fun onDisable() {
        pulse?.close()
        if (::context.isInitialized) context.close()
    }

    @EventHandler
    fun join(event: PlayerJoinEvent) { joins.incrementAndGet() }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty() || args[0].equals("status", true)) {
            DemoEvent().callEvent()
            sender.sendMessage("§8[§bpnDemo§8] §7API §f1 §7· context §f${context.id} §7· online §f${Bukkit.getOnlinePlayers().size}")
            sender.sendMessage("§7Используются: lifecycle, metrics, diagnostics, tasks, currency, placeholders")
            return true
        }
        if (sender !is Player) { sender.sendMessage("Only players can use this demo action."); return true }
        when (args[0].lowercase()) {
            "balance" -> currency.balance(sender.uniqueId).thenAccept { sender.sendMessage("§bБаланс: §f${currency.format(it)}") }
            "give" -> {
                val cooldown = context.cooldowns.acquire(sender.uniqueId, "give", Duration.ofSeconds(2))
                if (!cooldown.allowed) {
                    sender.sendMessage("§eПодождите ${cooldown.remaining.toMillis()} мс перед следующей операцией.")
                    return true
                }
                val amount = args.getOrNull(1)?.toBigDecimalOrNull()
                if (amount == null || amount <= BigDecimal.ZERO) sender.sendMessage("§cИспользование: /pndemo give <amount>")
                else currency.deposit(sender.uniqueId, amount).thenAccept { sender.sendMessage("§a${it.status}: §f${currency.format(it.currentBalance ?: BigDecimal.ZERO)}") }
            }
            "reload" -> { reloadConfig(); sender.sendMessage("§aКонфигурация pnDemo перезагружена.") }
            else -> sender.sendMessage("§7/pndemo status|balance|give <amount>|reload")
        }
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> =
        if (args.size == 1) listOf("status", "balance", "give", "reload").filter { it.startsWith(args[0], true) } else emptyList()

    private fun mutate(id: UUID, amount: BigDecimal, add: Boolean): CurrencyResult {
        val old = balances[id] ?: BigDecimal.ZERO.setScale(2)
        val next = if (add) old + amount else old - amount
        if (next < BigDecimal.ZERO) return CurrencyResult.rejected(CurrencyRejectReason.INSUFFICIENT_FUNDS)
        balances[id] = next.setScale(2, RoundingMode.DOWN)
        return CurrencyResult.success(old, balances[id])
    }

    private class PnDemoEventListener(private val plugin: DemoPlugin) : PnListener {
        @PnEventHandler
        fun onDemo(event: DemoEvent) { plugin.logger.info("received ${event.eventName}") }
    }

    private class DemoEvent : Event()
}
