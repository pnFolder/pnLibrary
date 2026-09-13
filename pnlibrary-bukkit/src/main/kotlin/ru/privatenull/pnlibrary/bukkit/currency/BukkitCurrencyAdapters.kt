package ru.privatenull.pnlibrary.bukkit.currency

import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import ru.privatenull.pnlibrary.api.currency.*
import java.math.BigDecimal
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

internal object BukkitCurrencyAdapters {
    fun vault(plugin: Plugin): CurrencyProvider? = runCatching {
        val type = Class.forName("net.milkbowl.vault.economy.Economy")
        @Suppress("UNCHECKED_CAST")
        val economy = plugin.server.servicesManager.load(type as Class<Any>) ?: return null
        ReflectiveVaultCurrency(economy)
    }.getOrNull()

    fun playerPoints(plugin: Plugin): CurrencyProvider? = runCatching {
        val target = plugin.server.pluginManager.getPlugin("PlayerPoints") ?: return null
        val api = target.javaClass.methods.firstOrNull { it.name == "getAPI" && it.parameterCount == 0 }
            ?.invoke(target) ?: return null
        ReflectivePlayerPointsCurrency(api)
    }.getOrNull()
}

private class ReflectiveVaultCurrency(private val economy: Any) :
    CurrencyProvider, CurrencyDeposits, CurrencyWithdrawals, CurrencyFormatting {
    private val methods = economy.javaClass.methods.toList()
    private val digits = invoke("fractionalDigits")?.toString()?.toIntOrNull()?.coerceIn(0, 18) ?: 2
    override val descriptor = CurrencyDescriptor(
        displayName = invoke("currencyNamePlural")?.toString()?.ifBlank { "Money" } ?: "Money",
        symbol = "",
        fractionDigits = digits,
        roundingMode = java.math.RoundingMode.DOWN,
    )

    override fun balance(account: CurrencyAccount) = completed {
        decimal(invokePlayer("getBalance", account, null))
    }
    override fun deposit(account: CurrencyAccount, amount: BigDecimal) = transaction("depositPlayer", account, amount)
    override fun withdraw(account: CurrencyAccount, amount: BigDecimal) = transaction("withdrawPlayer", account, amount)
    override fun format(amount: BigDecimal): String =
        invoke("format", amount.toDouble())?.toString() ?: descriptor.normalize(amount).toPlainString()

    private fun transaction(name: String, account: CurrencyAccount, amount: BigDecimal) = completed {
        val response = invokePlayer(name, account, amount) ?: return@completed CurrencyResult.unavailable("Vault returned no response")
        val success = response.javaClass.methods.firstOrNull { it.name == "transactionSuccess" && it.parameterCount == 0 }
            ?.invoke(response) as? Boolean ?: false
        val balance = response.javaClass.fields.firstOrNull { it.name == "balance" }?.get(response)?.let(::decimal)
        val error = response.javaClass.fields.firstOrNull { it.name == "errorMessage" }?.get(response)?.toString()
        if (success) CurrencyResult.success(current = balance)
        else CurrencyResult.rejected(CurrencyRejectReason.PROVIDER_REJECTED, error)
    }

    private fun invokePlayer(name: String, account: CurrencyAccount, amount: BigDecimal?): Any? {
        val player = Bukkit.getOfflinePlayer(account.playerId)
        val method = methods.firstOrNull { candidate ->
            candidate.name == name && candidate.parameterCount == (if (amount == null) 1 else 2) &&
                candidate.parameterTypes[0].isInstance(player)
        } ?: error("Vault provider ${economy.javaClass.name} does not implement $name(OfflinePlayer${if (amount == null) "" else ", double"})")
        return if (amount == null) method.invoke(economy, player) else method.invoke(economy, player, amount.toDouble())
    }

    private fun invoke(name: String, vararg arguments: Any): Any? = methods.firstOrNull {
        it.name == name && it.parameterCount == arguments.size
    }?.invoke(economy, *arguments)
}

private class ReflectivePlayerPointsCurrency(private val api: Any) :
    CurrencyProvider, CurrencyDeposits, CurrencyWithdrawals, CurrencyBalanceMutation, CurrencyReset {
    private val methods = api.javaClass.methods.toList()
    override val descriptor = CurrencyDescriptor("Points", "", 0, java.math.RoundingMode.DOWN)

    override fun balance(account: CurrencyAccount) = completed {
        decimal(call(listOf("lookUp", "getPoints"), account.playerId))
    }
    override fun deposit(account: CurrencyAccount, amount: BigDecimal) = mutation(listOf("give", "add"), account, amount)
    override fun withdraw(account: CurrencyAccount, amount: BigDecimal) = mutation(listOf("take", "remove"), account, amount)
    override fun setBalance(account: CurrencyAccount, amount: BigDecimal) = mutation(listOf("set"), account, amount)
    override fun reset(account: CurrencyAccount) = completed {
        result(call(listOf("reset"), account.playerId), "PlayerPoints rejected reset")
    }

    private fun mutation(names: List<String>, account: CurrencyAccount, amount: BigDecimal) = completed {
        result(call(names, account.playerId, amount.intValueExact()), "PlayerPoints rejected ${names.first()}")
    }

    private fun call(names: List<String>, vararg arguments: Any): Any? {
        val method = methods.firstOrNull { it.name in names && it.parameterCount == arguments.size }
            ?: error("PlayerPoints API ${api.javaClass.name} does not implement ${names.joinToString("/")}")
        return method.invoke(api, *arguments)
    }

    private fun result(value: Any?, failure: String): CurrencyResult =
        if (value !is Boolean || value) CurrencyResult.success()
        else CurrencyResult.rejected(CurrencyRejectReason.PROVIDER_REJECTED, failure)
}

private fun decimal(value: Any?): BigDecimal = when (value) {
    is BigDecimal -> value
    is Number -> BigDecimal(value.toString())
    else -> error("Currency provider returned a non-numeric balance: $value")
}

private fun <T> completed(operation: () -> T): CompletionStage<T> = try {
    CompletableFuture.completedFuture(operation())
} catch (error: Throwable) {
    CompletableFuture<T>().also { it.completeExceptionally(error.cause ?: error) }
}
