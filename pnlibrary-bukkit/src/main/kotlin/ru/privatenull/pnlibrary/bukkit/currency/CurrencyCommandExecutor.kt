package ru.privatenull.pnlibrary.bukkit.currency

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import ru.privatenull.pnlibrary.api.currency.*
import java.math.BigDecimal
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.CompletionStage

internal class CurrencyCommandExecutor(
    private val plugin: Plugin,
    private val currencies: CurrencyProviderRegistry,
) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty() || args[0].equals("help", true)) return help(sender)
        if (args[0].equals("list", true)) {
            if (!allowed(sender, "pnlibrary.currency.list")) return true
            val values = currencies.all().joinToString(", ") { it.key.toString() }
            info(sender, if (values.isEmpty()) "No currencies are currently available." else values)
            return true
        }
        val currency = currencies.get(args[0])
        if (currency == null) {
            error(sender, "Unknown or unavailable currency: ${args[0]}")
            return true
        }
        val settings = currency.extension(CurrencyCommandSettings::class.java) ?: DEFAULT_MESSAGES
        if (!settings.enabled) return error(sender, "Commands are disabled for ${currency.key}.")
        if (args.size < 2) return usage(sender, currency)
        val operation = args[1].lowercase()
        val permission = "${settings.permissionPrefix ?: "pnlibrary.currency.${permissionPart(currency.key.toString())}"}.$operation"
        if (!allowed(sender, permission)) return true
        return when (operation) {
            "balance" -> balance(sender, currency, args.drop(2))
            "add" -> mutate(sender, currency, args.drop(2), "add", currency::deposit)
            "take" -> mutate(sender, currency, args.drop(2), "take", currency::withdraw)
            "set" -> mutate(sender, currency, args.drop(2), "set", currency::setBalance)
            "reset" -> reset(sender, currency, args.drop(2))
            "pay" -> pay(sender, currency, args.drop(2))
            "history" -> history(sender, currency, args.drop(2))
            else -> usage(sender, currency)
        }
    }

    private fun balance(sender: CommandSender, currency: Currency, args: List<String>): Boolean {
        val target = if (args.isEmpty()) (sender as? Player)?.uniqueId else playerId(args[0])
        if (target == null) return error(sender, "Specify a player.")
        complete(sender, currency.balance(target)) { amount -> configured(sender, currency, settings(currency).balance, mapOf("balance" to currency.format(amount))) }
        return true
    }

    private fun mutate(sender: CommandSender, currency: Currency, args: List<String>, operation: String, call: (UUID, BigDecimal) -> CompletionStage<CurrencyResult>): Boolean {
        if (args.size < 2) return error(sender, "Usage: /pncurrency ${currency.key} $operation <player> <amount>")
        val target = playerId(args[0]) ?: return error(sender, "Player not found: ${args[0]}")
        val amount = args[1].toBigDecimalOrNull() ?: return error(sender, "Invalid amount: ${args[1]}")
        val ledger = currency.extension(CurrencyLedger::class.java)
        val type = when (operation) { "add" -> CurrencyTransactionType.CREDIT; "take" -> CurrencyTransactionType.DEBIT; else -> CurrencyTransactionType.SET_BALANCE }
        val stage = if (ledger == null) call(target, amount) else ledger.transact(
            CurrencyTransactionRequest(
                type = type,
                amount = amount,
                source = if (type == CurrencyTransactionType.DEBIT) CurrencyAccount(target) else null,
                target = if (type == CurrencyTransactionType.DEBIT) null else CurrencyAccount(target),
                actor = actor(sender),
                service = "pnlibrary.command",
                reason = "Command /pncurrency $operation",
            )
        ).thenApply(::transactionResult)
        complete(sender, stage) { result -> showResult(sender, currency, result, amount) }
        return true
    }

    private fun reset(sender: CommandSender, currency: Currency, args: List<String>): Boolean {
        val target = args.firstOrNull()?.let(::playerId) ?: return error(sender, "Specify a player.")
        val ledger = currency.extension(CurrencyLedger::class.java)
        val stage = ledger?.transact(CurrencyTransactionRequest(
            CurrencyTransactionType.RESET, BigDecimal.ZERO, target = CurrencyAccount(target),
            actor = actor(sender), service = "pnlibrary.command", reason = "Command /pncurrency reset",
        ))?.thenApply(::transactionResult) ?: currency.reset(target)
        complete(sender, stage) { result -> showResult(sender, currency, result, BigDecimal.ZERO) }
        return true
    }

    private fun pay(sender: CommandSender, currency: Currency, args: List<String>): Boolean {
        val source = sender as? Player ?: return error(sender, "Only a player can use pay.")
        if (args.size < 2) return error(sender, "Usage: /pncurrency ${currency.key} pay <player> <amount>")
        val target = playerId(args[0]) ?: return error(sender, "Player not found: ${args[0]}")
        val amount = args[1].toBigDecimalOrNull() ?: return error(sender, "Invalid amount: ${args[1]}")
        val ledger = currency.extension(CurrencyLedger::class.java)
        val stage = ledger?.transact(CurrencyTransactionRequest(
            CurrencyTransactionType.TRANSFER, amount,
            source = CurrencyAccount(source.uniqueId), target = CurrencyAccount(target),
            actor = CurrencyActor.player(source.uniqueId), service = "pnlibrary.command",
            reason = "Player payment",
        ))?.thenApply(::transactionResult) ?: currency.transfer(source.uniqueId, target, amount)
        complete(sender, stage) { result -> showResult(sender, currency, result, amount) }
        return true
    }

    private fun history(sender: CommandSender, currency: Currency, args: List<String>): Boolean {
        val ledger = currency.extension(CurrencyLedger::class.java)
            ?: return error(sender, "${currency.key} does not provide transaction history.")
        val account = args.firstOrNull()?.let(::playerId)
        complete(sender, ledger.history(CurrencyHistoryQuery(account = account, limit = 10))) { page ->
            if (page.items.isEmpty()) configured(sender, currency, settings(currency).historyEmpty)
            page.items.forEach { transaction ->
                val time = TIME.format(transaction.createdAt)
                info(sender, "#$time ${transaction.type} ${currency.format(transaction.amount)} · ${transaction.actor.type}:${transaction.actor.id} · ${transaction.reason ?: transaction.service}")
            }
        }
        return true
    }

    private fun showResult(sender: CommandSender, currency: Currency, result: CurrencyResult, amount: BigDecimal) {
        val settings = settings(currency)
        if (result.isSuccess) configured(sender, currency, settings.success, mapOf("amount" to currency.format(amount)))
        else configured(sender, currency, settings.failure, mapOf("error" to (result.message ?: result.reason?.name ?: result.status.name)))
    }

    private fun transactionResult(transaction: CurrencyTransaction): CurrencyResult = when (transaction.status) {
        CurrencyTransactionStatus.COMMITTED -> CurrencyResult.success(
            transaction.sourceBalanceBefore ?: transaction.targetBalanceBefore,
            transaction.sourceBalanceAfter ?: transaction.targetBalanceAfter,
        )
        CurrencyTransactionStatus.REJECTED -> CurrencyResult.rejected(CurrencyRejectReason.PROVIDER_REJECTED, transaction.failure)
        CurrencyTransactionStatus.FAILED -> CurrencyResult.failed(IllegalStateException(transaction.failure ?: "Currency transaction failed"))
    }

    private fun actor(sender: CommandSender): CurrencyActor =
        if (sender is Player) CurrencyActor.player(sender.uniqueId) else CurrencyActor.server()

    private fun <T> complete(sender: CommandSender, stage: CompletionStage<T>, success: (T) -> Unit) {
        stage.whenComplete { value, failure -> Bukkit.getScheduler().runTask(plugin, Runnable {
            if (failure == null) success(value) else error(sender, failure.cause?.message ?: failure.message ?: "Currency operation failed")
        }) }
    }

    private fun usage(sender: CommandSender, currency: Currency): Boolean {
        info(sender, "/pncurrency ${currency.key} <balance|add|take|set|reset|pay|history>")
        return true
    }
    private fun help(sender: CommandSender): Boolean {
        info(sender, "/pncurrency list")
        info(sender, "/pncurrency <namespace:name> <operation> ...")
        return true
    }
    private fun allowed(sender: CommandSender, permission: String): Boolean {
        if (sender.hasPermission(permission)) return true
        error(sender, "Missing permission: $permission")
        return false
    }
    private fun playerId(name: String): UUID? = Bukkit.getPlayerExact(name)?.uniqueId
        ?: Bukkit.getOfflinePlayers().firstOrNull { it.name.equals(name, true) }?.uniqueId
    private fun permissionPart(value: String) = value.lowercase().replace(Regex("[^a-z0-9_.-]"), ".")
    private fun settings(currency: Currency) = currency.extension(CurrencyCommandSettings::class.java) ?: DEFAULT_MESSAGES
    private fun configured(sender: CommandSender, currency: Currency, template: String, values: Map<String, String> = emptyMap()) {
        var message = settings(currency).prefix.replace("{currency}", currency.descriptor.displayName)
        var body = template
        values.forEach { (key, value) -> body = body.replace("{$key}", value) }
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', message + body))
    }
    private fun info(sender: CommandSender, message: String) { sender.sendMessage("§8[§6Currency§8] §f$message") }
    private fun success(sender: CommandSender, message: String) { sender.sendMessage("§8[§6Currency§8] §a$message") }
    private fun error(sender: CommandSender, message: String): Boolean { sender.sendMessage("§8[§6Currency§8] §c$message"); return true }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> = when (args.size) {
        1 -> (listOf("list") + currencies.all().map { it.key.toString() }).matching(args[0])
        2 -> listOf("balance", "add", "take", "set", "reset", "pay", "history").matching(args[1])
        3 -> Bukkit.getOnlinePlayers().map(Player::getName).matching(args[2])
        else -> emptyList()
    }

    private fun List<String>.matching(input: String) = filter { it.startsWith(input, true) }.sorted()

    companion object {
        private val TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())
        private val DEFAULT_MESSAGES = CurrencyCommandSettings(
            true, null, "§8[§6{currency}§8] ", "§aOperation completed: {amount}",
            "§c{error}", "§fBalance: §a{balance}", "§7No transactions found.",
        )
    }
}
