package org.pnfolder.pnlibrary.bukkit.currency

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.command.ConsoleCommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.pnfolder.pnlibrary.api.commands.ArgumentType
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.commands.CommandDefinition
import org.pnfolder.pnlibrary.api.commands.command
import org.pnfolder.pnlibrary.api.currency.Currency
import org.pnfolder.pnlibrary.api.currency.CurrencyAccount
import org.pnfolder.pnlibrary.api.currency.CurrencyActor
import org.pnfolder.pnlibrary.api.currency.CurrencyCommandSettings
import org.pnfolder.pnlibrary.api.currency.CurrencyConfirmationMode
import org.pnfolder.pnlibrary.api.currency.CurrencyHistoryQuery
import org.pnfolder.pnlibrary.api.currency.CurrencyLedger
import org.pnfolder.pnlibrary.api.currency.CurrencyProviderRegistry
import org.pnfolder.pnlibrary.api.currency.CurrencyRejectReason
import org.pnfolder.pnlibrary.api.currency.CurrencyResult
import org.pnfolder.pnlibrary.api.currency.CurrencyTransaction
import org.pnfolder.pnlibrary.api.currency.CurrencyTransactionRequest
import org.pnfolder.pnlibrary.api.currency.CurrencyTransactionStatus
import org.pnfolder.pnlibrary.api.currency.CurrencyTransactionType
import org.pnfolder.pnlibrary.bukkit.commands.BukkitCommandSender
import org.pnfolder.pnlibrary.common.text.RussianText
import org.pnfolder.pnlibrary.api.chat.ChatKit
import org.pnfolder.pnlibrary.api.chat.ChatNotice
import org.pnfolder.pnlibrary.api.chat.ChatPalette
import org.pnfolder.pnlibrary.api.chat.ChatReader
import org.pnfolder.pnlibrary.api.chat.ChatTone
import java.math.BigDecimal
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.CompletionStage

/**
 * Builds and executes administrator-facing portable currency commands.
 *
 * Mutations honor provider capabilities and configured console-confirmation requirements.
 * Asynchronous operation results are delivered through the server scheduler. Replies use the shared chat style;
 * the configured templates of a currency keep their own legacy colors after the notice mark.
 */
internal class CurrencyCommandExecutor(
    private val plugin: Plugin,
    private val currencies: CurrencyProviderRegistry,
) {
    private val confirmations = CurrencyConfirmationStore()

    /** Builds the currency command tree with capability-aware permissions and suggestions. */
    fun definition(): CommandDefinition = command("money") {
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
        val sender = context.sender as? BukkitCommandSender
        if (sender == null) {
            ChatKit(ChatReader.of(context.sender))
                .notice(ChatNotice(ChatTone.FAILURE, "Команда недоступна этому отправителю."))
                .forEach(context.sender::send)
        } else {
            execute(sender, context.arguments)
        }
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

    private fun execute(sender: BukkitCommandSender, args: List<String>): Boolean {
        confirmations.discardExpired()
        if (args.isEmpty() || args[0].equals("help", true)) return help(sender)
        if (args[0].equals("confirm", true)) return confirm(sender, args.getOrNull(1))
        if (args[0].equals("cancel", true)) return cancel(sender, args.getOrNull(1))
        if (args[0].equals("list", true)) {
            if (!allowed(sender, "pnlibrary.currency.list")) return true
            return list(sender)
        }
        val currency = currencies.get(args[0])
            ?: return error(sender, "Валюта не найдена или недоступна: ${args[0]}")
        val settings = settings(currency)
        if (!settings.enabled) return error(sender, "Команды валюты ${currency.key} отключены.")
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

    /** Every available currency: its name, then the key used in commands. */
    private fun list(sender: BukkitCommandSender): Boolean {
        val all = currencies.all()
        if (all.isEmpty()) return notify(sender, ChatTone.INFO, "Сейчас нет доступных валют.")
        val kit = kit(sender)
        val word = RussianText.plural(all.size.toLong(), "валюта", "валюты", "валют")
        val rows = all.map { currency ->
            kit.text("  ${currency.descriptor.displayName}", ChatPalette.TEXT).append(kit.dot())
                .append(kit.text(currency.key.toString(), ChatPalette.SOFT))
        }
        kit.framed(listOf(kit.header("Валюты", kit.chip(all.size, word, ChatPalette.ACCENT)), kit.blank()) + rows)
            .forEach(sender::send)
        return true
    }

    private fun balance(sender: BukkitCommandSender, currency: Currency, args: List<String>): Boolean {
        val target = if (args.isEmpty()) (sender.native as? Player)?.uniqueId else playerId(args[0])
        if (target == null) return error(sender, "Укажите игрока.")
        complete(sender, currency.balance(target)) { amount ->
            configured(sender, currency, ChatTone.INFO, settings(currency).balance, mapOf("balance" to currency.format(amount)))
        }
        return true
    }

    private fun mutate(
        sender: BukkitCommandSender,
        currency: Currency,
        args: List<String>,
        operation: String,
        call: (UUID, BigDecimal) -> CompletionStage<CurrencyResult>,
    ): Boolean {
        if (args.size < 2) return error(sender, "Использование: /pn money ${currency.key} $operation <игрок> <сумма>")
        val target = playerId(args[0]) ?: return error(sender, "Игрок не найден: ${args[0]}")
        val amount = args[1].toBigDecimalOrNull() ?: return error(sender, "Неверная сумма: ${args[1]}")
        val ledger = currency.extension(CurrencyLedger::class.java)
        val type = when (operation) {
            "add" -> CurrencyTransactionType.CREDIT
            "take" -> CurrencyTransactionType.DEBIT
            else -> CurrencyTransactionType.SET_BALANCE
        }
        val execute = {
            if (ledger == null) call(target, amount) else ledger.transact(CurrencyTransactionRequest(
                type = type,
                amount = amount,
                source = if (type == CurrencyTransactionType.DEBIT) CurrencyAccount(target) else null,
                target = if (type == CurrencyTransactionType.DEBIT) null else CurrencyAccount(target),
                actor = actor(sender),
                service = "pnlibrary.command",
                reason = "Command /pn money $operation",
                metadata = mapOf("requestedBy" to sender.name, "approval" to "console"),
            )).thenApply(::transactionResult)
        }
        return queueOrRun(sender, currency, "$operation ${args[0]} ${amount.toPlainString()}", execute) { result ->
            showResult(sender, currency, result, amount)
        }
    }

    private fun reset(sender: BukkitCommandSender, currency: Currency, args: List<String>): Boolean {
        val target = args.firstOrNull()?.let(::playerId) ?: return error(sender, "Укажите игрока.")
        val ledger = currency.extension(CurrencyLedger::class.java)
        val execute = {
            ledger?.transact(CurrencyTransactionRequest(
                CurrencyTransactionType.RESET, BigDecimal.ZERO, target = CurrencyAccount(target),
                actor = actor(sender), service = "pnlibrary.command", reason = "Command /pn money reset",
                metadata = mapOf("requestedBy" to sender.name, "approval" to "console"),
            ))?.thenApply(::transactionResult) ?: currency.reset(target)
        }
        return queueOrRun(sender, currency, "reset ${args[0]}", execute) { result ->
            showResult(sender, currency, result, BigDecimal.ZERO)
        }
    }

    private fun pay(sender: BukkitCommandSender, currency: Currency, args: List<String>): Boolean {
        val source = sender.native as? Player ?: return error(sender, "Переводить валюту может только игрок.")
        if (args.size < 2) return error(sender, "Использование: /pn money ${currency.key} pay <игрок> <сумма>")
        val target = playerId(args[0]) ?: return error(sender, "Игрок не найден: ${args[0]}")
        val amount = args[1].toBigDecimalOrNull() ?: return error(sender, "Неверная сумма: ${args[1]}")
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

    /** The last ten transactions: time, kind, amount, who requested it and why. */
    private fun history(sender: BukkitCommandSender, currency: Currency, args: List<String>): Boolean {
        val ledger = currency.extension(CurrencyLedger::class.java)
            ?: return error(sender, "Валюта ${currency.key} не ведёт историю операций.")
        val account = args.firstOrNull()?.let(::playerId)
        complete(sender, ledger.history(CurrencyHistoryQuery(account = account, limit = 10))) { page ->
            if (page.items.isEmpty()) {
                configured(sender, currency, ChatTone.INFO, settings(currency).historyEmpty)
                return@complete
            }
            val kit = kit(sender)
            val rows = page.items.map { transaction ->
                kit.text("  ${TIME.format(transaction.createdAt)} ", ChatPalette.SOFT)
                    .append(kit.text(typeLabel(transaction.type), ChatPalette.ACCENT))
                    .append(kit.text(" ${currency.format(transaction.amount)}", ChatPalette.TEXT))
                    .append(kit.dot())
                    .append(kit.text("${transaction.actor.type}:${transaction.actor.id}", ChatPalette.LABEL))
                    .append(kit.dot())
                    .append(kit.text(transaction.reason ?: transaction.service, ChatPalette.LABEL))
            }
            kit.framed(listOf(kit.header("История", kit.text(currency.descriptor.displayName, ChatPalette.SOFT)), kit.blank()) + rows)
                .forEach(sender::send)
        }
        return true
    }

    private fun typeLabel(type: CurrencyTransactionType): String = when (type) {
        CurrencyTransactionType.CREDIT -> "начисление"
        CurrencyTransactionType.DEBIT -> "списание"
        CurrencyTransactionType.TRANSFER -> "перевод"
        CurrencyTransactionType.SET_BALANCE -> "установка баланса"
        CurrencyTransactionType.RESET -> "сброс"
    }

    private fun showResult(sender: BukkitCommandSender, currency: Currency, result: CurrencyResult, amount: BigDecimal) {
        val settings = settings(currency)
        if (result.isSuccess) {
            configured(sender, currency, ChatTone.SUCCESS, settings.success, mapOf("amount" to currency.format(amount)))
        } else {
            val error = result.message ?: result.reason?.name ?: result.status.name
            configured(sender, currency, ChatTone.FAILURE, settings.failure, mapOf("error" to error))
        }
    }

    private fun transactionResult(transaction: CurrencyTransaction): CurrencyResult = when (transaction.status) {
        CurrencyTransactionStatus.COMMITTED -> CurrencyResult.success(
            transaction.sourceBalanceBefore ?: transaction.targetBalanceBefore,
            transaction.sourceBalanceAfter ?: transaction.targetBalanceAfter,
        )
        CurrencyTransactionStatus.REJECTED -> CurrencyResult.rejected(CurrencyRejectReason.PROVIDER_REJECTED, transaction.failure)
        CurrencyTransactionStatus.FAILED -> CurrencyResult.failed(
            IllegalStateException(transaction.failure ?: "Транзакция не выполнена"),
        )
    }

    private fun actor(sender: BukkitCommandSender): CurrencyActor =
        (sender.native as? Player)?.let { CurrencyActor.player(it.uniqueId) } ?: CurrencyActor.server()

    private fun <T> complete(sender: BukkitCommandSender, stage: CompletionStage<T>, success: (T) -> Unit) {
        stage.whenComplete { value, failure -> Bukkit.getScheduler().runTask(plugin, Runnable {
            if (failure == null) success(value)
            else error(sender, "Операция с валютой не выполнена.", failure.cause?.message ?: failure.message)
        }) }
    }

    private fun queueOrRun(
        sender: BukkitCommandSender,
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
        val operation = confirmations.add(
            settings.confirmationTimeoutSeconds, currency.key.toString(), description, sender.name, execute, completed,
        ) ?: return error(sender, "Слишком много операций с валютой ждут подтверждения.")
        val token = operation.token
        val console = Bukkit.getConsoleSender()
        val kit = ChatKit(ChatReader.CONSOLE)
        val request = "${currency.key} · $description · запросил ${sender.name}"
        val confirm = "Подтвердите в течение ${settings.confirmationTimeoutSeconds} с: /pn money confirm $token"
        (kit.notice(ChatNotice(ChatTone.WARNING, "Операция с валютой ждёт подтверждения: $token", request)) +
            kit.notice(ChatNotice(ChatTone.INFO, confirm)))
            .forEach { console.sendMessage(LEGACY.serialize(it)) }
        return notify(sender, ChatTone.PROGRESS, "Операция ждёт подтверждения в консоли.", "Код: $token")
    }

    private fun confirm(sender: BukkitCommandSender, token: String?): Boolean {
        if (sender.native !is ConsoleCommandSender) return error(sender, "Подтверждать операции с валютой может только консоль сервера.")
        if (token == null) return error(sender, "Использование: /pn money confirm <код>")
        val operation = confirmations.take(token) ?: return error(sender, "Код подтверждения неверен или устарел.")
        if (operation.expiresAt < System.currentTimeMillis()) return error(sender, "Срок действия кода подтверждения истёк.")
        notify(
            sender, ChatTone.SUCCESS, "Подтверждено: ${operation.currency} · ${operation.description}",
            "Запросил ${operation.requestedBy}",
        )
        complete(sender, operation.execute(), operation.completed)
        return true
    }

    private fun cancel(sender: BukkitCommandSender, token: String?): Boolean {
        if (sender.native !is ConsoleCommandSender) return error(sender, "Отменять операции с валютой может только консоль сервера.")
        if (token == null || confirmations.take(token) == null) return error(sender, "Код подтверждения неверен или устарел.")
        return notify(sender, ChatTone.SUCCESS, "Операция с валютой отменена: ${token.uppercase()}")
    }

    private fun usage(sender: BukkitCommandSender, currency: Currency): Boolean =
        notify(sender, ChatTone.WARNING, "Укажите действие.", "/pn money ${currency.key} <balance|add|take|set|reset|pay|history>")

    private fun help(sender: BukkitCommandSender): Boolean {
        val kit = kit(sender)
        val hints = kit.commandHints(
            "/pn money list" to "список валют",
            "/pn money <валюта> balance [игрок]" to "баланс",
            "/pn money <валюта> pay <игрок> <сумма>" to "перевести игроку",
            "/pn money <валюта> history [игрок]" to "последние операции",
            "/pn money <валюта> add <игрок> <сумма>" to "начислить",
            "/pn money <валюта> take <игрок> <сумма>" to "списать",
            "/pn money <валюта> set <игрок> <сумма>" to "установить баланс",
            "/pn money <валюта> reset <игрок>" to "обнулить баланс",
            "/pn money confirm <код>" to "подтвердить операцию из консоли",
            "/pn money cancel <код>" to "отменить операцию из консоли",
        )
        val intro = kit.note(if (kit.reader.interactive) "Нажмите на команду, чтобы вставить её в чат." else "Доступные действия зависят от прав.")
        kit.framed(listOf(kit.header("Валюты", kit.text("помощь", ChatPalette.LABEL)), intro, kit.blank()) + hints).forEach(sender::send)
        return true
    }

    private fun allowed(sender: BukkitCommandSender, permission: String): Boolean {
        if (sender.hasPermission(permission)) return true
        error(sender, "Недостаточно прав.", "Нужно право $permission")
        return false
    }

    private fun playerId(name: String): UUID? = Bukkit.getPlayerExact(name)?.uniqueId
        ?: Bukkit.getOfflinePlayers().firstOrNull { it.name.equals(name, true) }?.uniqueId
    private fun permissionPart(value: String) = value.lowercase().replace(Regex("[^a-z0-9_.-]"), ".")
    private fun settings(currency: Currency) = currency.extension(CurrencyCommandSettings::class.java) ?: DEFAULT_MESSAGES
    private fun kit(sender: BukkitCommandSender) = ChatKit(ChatReader.of(sender))

    /**
     * A configured template of [currency] after the notice mark of [tone]. The template keeps its own legacy
     * colors; the mark is a sibling, so uncolored template text does not take the tone's color.
     */
    private fun configured(
        sender: BukkitCommandSender,
        currency: Currency,
        tone: ChatTone,
        template: String,
        values: Map<String, String> = emptyMap(),
    ) {
        val prefix = settings(currency).prefix.replace("{currency}", currency.descriptor.displayName)
        var body = template
        values.forEach { (key, value) -> body = body.replace("{$key}", value) }
        val message = LEGACY.deserialize(ChatColor.translateAlternateColorCodes('&', prefix + body))
        sender.send(Component.empty().append(kit(sender).mark(tone)).append(message))
    }

    private fun notify(sender: BukkitCommandSender, tone: ChatTone, text: String, detail: String? = null): Boolean {
        kit(sender).notice(ChatNotice(tone, text, detail)).forEach(sender::send)
        return true
    }

    private fun error(sender: BukkitCommandSender, text: String, detail: String? = null): Boolean =
        notify(sender, ChatTone.FAILURE, text, detail)

    companion object {
        private val TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())
        private val DEFAULT_MESSAGES = CurrencyCommandSettings()
        private val LEGACY = LegacyComponentSerializer.legacySection()
    }
}
