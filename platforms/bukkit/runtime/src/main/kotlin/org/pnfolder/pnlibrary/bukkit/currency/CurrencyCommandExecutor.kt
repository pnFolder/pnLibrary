package org.pnfolder.pnlibrary.bukkit.currency

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.pnfolder.pnlibrary.api.chat.ChatKit
import org.pnfolder.pnlibrary.api.chat.ChatNotice
import org.pnfolder.pnlibrary.api.chat.ChatReader
import org.pnfolder.pnlibrary.api.chat.ChatTone
import org.pnfolder.pnlibrary.api.commands.ArgumentType
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.commands.CommandDefinition
import org.pnfolder.pnlibrary.api.commands.command
import org.pnfolder.pnlibrary.api.currency.CurrencyProviderRegistry
import org.pnfolder.pnlibrary.bukkit.commands.BukkitCommandSender

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
    private val operations = CurrencyOperations(plugin, currencies, confirmations)

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
        val permission = "${settings.permissionPrefix ?: "pnlibrary.currency.${permissionPart(currency.key.toString())}"}.$operation"
        if (!allowed(sender, permission)) return true
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

    private fun permissionPart(value: String) = value.lowercase().replace(Regex("[^a-z0-9_.-]"), ".")
}
