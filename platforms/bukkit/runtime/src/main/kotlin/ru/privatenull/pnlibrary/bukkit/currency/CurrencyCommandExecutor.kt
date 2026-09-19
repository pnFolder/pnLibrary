package ru.privatenull.pnlibrary.bukkit.currency

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.command.CommandSender
import org.bukkit.command.ConsoleCommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import ru.privatenull.pnlibrary.api.currency.*
import ru.privatenull.pnlibrary.api.commands.ArgumentType
import ru.privatenull.pnlibrary.api.commands.CommandContext
import ru.privatenull.pnlibrary.api.commands.CommandDefinition
import ru.privatenull.pnlibrary.api.commands.command
import ru.privatenull.pnlibrary.bukkit.commands.BukkitCommandSender
import java.math.BigDecimal
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.CompletionStage
import java.util.concurrent.ConcurrentHashMap
import java.security.SecureRandom

internal class CurrencyCommandExecutor(
    private val plugin: Plugin,
    private val currencies: CurrencyProviderRegistry,
) {
    private val pending = ConcurrentHashMap<String, PendingOperation>()
    private val random = SecureRandom()

    fun definition(): CommandDefinition = command("pncurrency") {
        aliases("pncurrencies")
        executes(::executePortable)
        literal("list") {
            permission("pnlibrary.currency.list")
            executes(::executePortable)
        }
        literal("confirm") {
            availableIf { it.sender.isConsole }
            argument("code", ArgumentType.string()) { executes(::executePortable) }
        }
        literal("cancel") {
            availableIf { it.sender.isConsole }
            argument("code", ArgumentType.string()) { executes(::executePortable) }
        }
        argument("currency", ArgumentType.string()) {
            suggests { currencies.all().map { it.key.toString() } }
            literal("balance") {
                availableIf { operationAllowed(it, "balance") }
                executes(::executePortable)
                argument("player", ArgumentType.string()) {
                    suggests { onlinePlayers() }
                    executes(::executePortable)
                }
            }
            literal("add") {
                availableIf { operationAllowed(it, "add") }
                argument("player", ArgumentType.string()) {
                    suggests { onlinePlayers() }
                    argument("amount", ArgumentType.decimal()) { executes(::executePortable) }
                }
            }
            literal("take") {
                availableIf { operationAllowed(it, "take") }
                argument("player", ArgumentType.string()) {
                    suggests { onlinePlayers() }
                    argument("amount", ArgumentType.decimal()) { executes(::executePortable) }
                }
            }
            literal("set") {
                availableIf { operationAllowed(it, "set") }
                argument("player", ArgumentType.string()) {
                    suggests { onlinePlayers() }
                    argument("amount", ArgumentType.decimal()) { executes(::executePortable) }
                }
            }
            literal("reset") {
                availableIf { operationAllowed(it, "reset") }
                argument("player", ArgumentType.string()) {
                    suggests { onlinePlayers() }
                    executes(::executePortable)
                }
            }
            literal("pay") {
                availableIf { operationAllowed(it, "pay") }
                argument("player", ArgumentType.string()) {
                    suggests { onlinePlayers() }
                    argument("amount", ArgumentType.decimal()) { executes(::executePortable) }
                }
            }
            literal("history") {
                availableIf { operationAllowed(it, "history") }
                executes(::executePortable)
                argument("player", ArgumentType.string()) {
                    suggests { onlinePlayers() }
                    executes(::executePortable)
                }
            }
        }
    }

    private fun executePortable(context: CommandContext) {
        val sender = (context.sender as? BukkitCommandSender)?.native
        if (sender == null) context.sender.send(net.kyori.adventure.text.Component.text("Unsupported Bukkit sender."))
        else execute(sender, context.arguments)
    }

    private fun operationAllowed(context: CommandContext, operation: String): Boolean {
        val currency = context.parsedValues["currency"] as? String ?: return false
        val resolved = currencies.get(currency) ?: return false
        val settings = settings(resolved)
        if (!settings.enabled) return false
        val permission = "${settings.permissionPrefix ?: "pnlibrary.currency.${permissionPart(resolved.key.toString())}"}.$operation"
        return context.sender.hasPermission(permission)
    }

    private fun onlinePlayers(): List<String> = Bukkit.getOnlinePlayers().map(Player::getName)

    private fun execute(sender: CommandSender, args: List<String>): Boolean {
        discardExpired()
        if (args.isEmpty() || args[0].equals("help", true)) return help(sender)
        if (args[0].equals("confirm", true)) return confirm(sender, args.getOrNull(1))
        if (args[0].equals("cancel", true)) return cancel(sender, args.getOrNull(1))
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
        val execute = {
            if (ledger == null) call(target, amount) else ledger.transact(CurrencyTransactionRequest(
                type = type,
                amount = amount,
                source = if (type == CurrencyTransactionType.DEBIT) CurrencyAccount(target) else null,
                target = if (type == CurrencyTransactionType.DEBIT) null else CurrencyAccount(target),
                actor = actor(sender),
                service = "pnlibrary.command",
                reason = "Command /pncurrency $operation",
                metadata = mapOf("requestedBy" to sender.name, "approval" to "console"),
            )).thenApply(::transactionResult)
        }
        return queueOrRun(sender, currency, "$operation ${args[0]} ${amount.toPlainString()}", execute) { result ->
            showResult(sender, currency, result, amount)
        }
    }

    private fun reset(sender: CommandSender, currency: Currency, args: List<String>): Boolean {
        val target = args.firstOrNull()?.let(::playerId) ?: return error(sender, "Specify a player.")
        val ledger = currency.extension(CurrencyLedger::class.java)
        val execute = {
            ledger?.transact(CurrencyTransactionRequest(
                CurrencyTransactionType.RESET, BigDecimal.ZERO, target = CurrencyAccount(target),
                actor = actor(sender), service = "pnlibrary.command", reason = "Command /pncurrency reset",
                metadata = mapOf("requestedBy" to sender.name, "approval" to "console"),
            ))?.thenApply(::transactionResult) ?: currency.reset(target)
        }
        return queueOrRun(sender, currency, "reset ${args[0]}", execute) { result ->
            showResult(sender, currency, result, BigDecimal.ZERO)
        }
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

    private fun queueOrRun(
        sender: CommandSender,
        currency: Currency,
        description: String,
        execute: () -> CompletionStage<CurrencyResult>,
        completed: (CurrencyResult) -> Unit,
    ): Boolean {
        val settings = settings(currency)
        if (settings.confirmationMode == CurrencyConfirmationMode.NONE) {
            complete(sender, execute(), completed)
            return true
        }
        if (pending.size >= 1_000) return error(sender, "Too many currency operations are awaiting confirmation.")
        val token = token()
        val expiresAt = System.currentTimeMillis() + settings.confirmationTimeoutSeconds * 1_000L
        pending[token] = PendingOperation(token, expiresAt, currency.key.toString(), description, sender.name, execute, completed)
        val console = Bukkit.getConsoleSender()
        console.sendMessage("§8[§6Currency confirmation§8] §e$token §f· ${currency.key} · $description · requested by ${sender.name}")
        console.sendMessage("§8[§6Currency confirmation§8] §fRun §e/pncurrency confirm $token §fwithin ${settings.confirmationTimeoutSeconds}s")
        info(sender, "Operation awaits console confirmation. Code: $token")
        return true
    }

    private fun confirm(sender: CommandSender, token: String?): Boolean {
        if (sender !is ConsoleCommandSender) return error(sender, "Only the server console can confirm currency operations.")
        if (token == null) return error(sender, "Usage: /pncurrency confirm <code>")
        val operation = pending.remove(token.uppercase()) ?: return error(sender, "Confirmation code is invalid or expired.")
        if (operation.expiresAt < System.currentTimeMillis()) return error(sender, "Confirmation code has expired.")
        info(sender, "Confirmed ${operation.currency} · ${operation.description} · requested by ${operation.requestedBy}")
        complete(sender, operation.execute(), operation.completed)
        return true
    }

    private fun cancel(sender: CommandSender, token: String?): Boolean {
        if (sender !is ConsoleCommandSender) return error(sender, "Only the server console can cancel currency operations.")
        if (token == null || pending.remove(token.uppercase()) == null) return error(sender, "Confirmation code is invalid or expired.")
        info(sender, "Pending currency operation cancelled: ${token.uppercase()}")
        return true
    }

    private fun discardExpired() {
        val now = System.currentTimeMillis()
        pending.entries.removeIf { it.value.expiresAt < now }
    }

    private fun token(): String {
        repeat(32) {
            val value = buildString(10) { repeat(10) { append(TOKEN_ALPHABET[random.nextInt(TOKEN_ALPHABET.length)]) } }
            if (!pending.containsKey(value)) return value
        }
        error("Unable to allocate a currency confirmation code")
    }

    private fun usage(sender: CommandSender, currency: Currency): Boolean {
        info(sender, "/pncurrency ${currency.key} <balance|add|take|set|reset|pay|history>")
        return true
    }
    private fun help(sender: CommandSender): Boolean {
        info(sender, "/pncurrency list")
        info(sender, "/pncurrency <namespace:name> <operation> ...")
        info(sender, "Console: /pncurrency <confirm|cancel> <code>")
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

    companion object {
        private val TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())
        private val DEFAULT_MESSAGES = CurrencyCommandSettings()
        private const val TOKEN_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    }

    private data class PendingOperation(
        val token: String,
        val expiresAt: Long,
        val currency: String,
        val description: String,
        val requestedBy: String,
        val execute: () -> CompletionStage<CurrencyResult>,
        val completed: (CurrencyResult) -> Unit,
    )
}
