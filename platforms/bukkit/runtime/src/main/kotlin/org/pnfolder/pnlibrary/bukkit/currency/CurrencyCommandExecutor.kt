package org.pnfolder.pnlibrary.bukkit.currency

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.pnfolder.pnlibrary.api.chat.ChatKit
import org.pnfolder.pnlibrary.api.chat.ChatNotice
import org.pnfolder.pnlibrary.api.chat.ChatReader
import org.pnfolder.pnlibrary.api.chat.ChatTone
import org.pnfolder.pnlibrary.api.commands.ArgumentType
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.commands.CommandDefinition
import org.pnfolder.pnlibrary.api.commands.command
import org.pnfolder.pnlibrary.api.currency.Currency
import org.pnfolder.pnlibrary.api.currency.CurrencyProviderRegistry
import org.pnfolder.pnlibrary.bukkit.commands.BukkitCommandSender
import org.pnfolder.pnlibrary.bukkit.tasks.ServerScheduler

/**
 * Builds and executes administrator-facing portable currency commands.
 *
 * Mutations honor provider capabilities and configured console-confirmation requirements.
 * Asynchronous operation results are delivered on the server thread. Replies use the shared chat style;
 * the configured templates of a currency keep their own legacy colors after the notice mark.
 */
internal class CurrencyCommandExecutor(
    private val currencies: CurrencyProviderRegistry,
    scheduler: ServerScheduler,
) {
    private val confirmations = CurrencyConfirmationStore()
    private val operations = CurrencyOperations(currencies, confirmations, scheduler)

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
            // The order is the order of the tab suggestions.
            listOf("balance", "add", "take", "set", "reset", "pay", "history").forEach { operation ->
                literal(operation) {
                    availableIf { operationAllowed(it, operation) }
                    if (operation in WITHOUT_PLAYER_ALLOWED) executes(::executePortable)
                    argument("player", ArgumentType.string()) {
                        suggests { onlinePlayers() }
                        if (operation in WITH_AMOUNT) {
                            argument("amount", ArgumentType.decimal()) { executes(::executePortable) }
                        } else {
                            executes(::executePortable)
                        }
                    }
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
        if (!settings(resolved).enabled) return false
        return context.sender.hasPermission(permission(resolved, operation))
    }

    private fun onlinePlayers(): List<String> = Bukkit.getOnlinePlayers().map(Player::getName)

    private fun execute(sender: BukkitCommandSender, args: List<String>): Boolean {
        confirmations.discardExpired()
        if (args.isEmpty() || args[0].equals("help", true)) return help(sender)
        if (args[0].equals("confirm", true)) return operations.confirm(sender, args.getOrNull(1))
        if (args[0].equals("cancel", true)) return operations.cancel(sender, args.getOrNull(1))
        if (args[0].equals("list", true)) {
            if (!allowed(sender, "pnlibrary.currency.list")) return true
            return operations.list(sender)
        }
        val currency = currencies.get(args[0])
            ?: return error(sender, "Валюта не найдена или недоступна: ${args[0]}")
        val settings = settings(currency)
        if (!settings.enabled) return error(sender, "Команды валюты ${currency.key} отключены.")
        if (args.size < 2) return usage(sender, currency)
        val operation = args[1].lowercase()
        if (!allowed(sender, permission(currency, operation))) return true
        return when (operation) {
            "balance" -> operations.balance(sender, currency, args.drop(2))
            "add" -> operations.mutate(sender, currency, args.drop(2), "add", currency::deposit)
            "take" -> operations.mutate(sender, currency, args.drop(2), "take", currency::withdraw)
            "set" -> operations.mutate(sender, currency, args.drop(2), "set", currency::setBalance)
            "reset" -> operations.reset(sender, currency, args.drop(2))
            "pay" -> operations.pay(sender, currency, args.drop(2))
            "history" -> operations.history(sender, currency, args.drop(2))
            else -> usage(sender, currency)
        }
    }

    private fun allowed(sender: BukkitCommandSender, permission: String): Boolean {
        if (sender.hasPermission(permission)) return true
        error(sender, "Недостаточно прав.", "Нужно право $permission")
        return false
    }

    private fun permission(currency: Currency, operation: String): String {
        val prefix = settings(currency).permissionPrefix
            ?: "pnlibrary.currency." + currency.key.toString().lowercase().replace(Regex("[^a-z0-9_.-]"), ".")
        return "$prefix.$operation"
    }
}

/** Operations that also work without a player name (they act on the sender). */
private val WITHOUT_PLAYER_ALLOWED = setOf("balance", "history")

/** Operations that take an amount after the player. */
private val WITH_AMOUNT = setOf("add", "take", "set", "pay")
