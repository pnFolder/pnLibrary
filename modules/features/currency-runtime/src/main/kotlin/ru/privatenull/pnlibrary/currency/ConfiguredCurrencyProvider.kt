package ru.privatenull.pnlibrary.currency

import ru.privatenull.pnlibrary.api.currency.CurrencyAccount
import ru.privatenull.pnlibrary.api.currency.CurrencyBalanceMutation
import ru.privatenull.pnlibrary.api.currency.CurrencyCapability
import ru.privatenull.pnlibrary.api.currency.CurrencyDeposits
import ru.privatenull.pnlibrary.api.currency.CurrencyDescriptor
import ru.privatenull.pnlibrary.api.currency.CurrencyFormatting
import ru.privatenull.pnlibrary.api.currency.CurrencyOperations
import ru.privatenull.pnlibrary.api.currency.CurrencyProvider
import ru.privatenull.pnlibrary.api.currency.CurrencyReset
import ru.privatenull.pnlibrary.api.currency.CurrencyResult
import ru.privatenull.pnlibrary.api.currency.CurrencyTransferOperation
import ru.privatenull.pnlibrary.api.currency.CurrencyTransfers
import ru.privatenull.pnlibrary.api.currency.CurrencyWithdrawals
import java.math.BigDecimal
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.function.BiFunction
import java.util.function.Function

/** Adapts Java-friendly operation callbacks into an immutable currency provider. */
internal class CurrencyOperationsBuilder : CurrencyOperations {
    private var balance: ((CurrencyAccount) -> CompletionStage<BigDecimal>)? = null
    private var deposit: ((CurrencyAccount, BigDecimal) -> CompletionStage<CurrencyResult>)? = null
    private var withdraw: ((CurrencyAccount, BigDecimal) -> CompletionStage<CurrencyResult>)? = null
    private var setBalance: ((CurrencyAccount, BigDecimal) -> CompletionStage<CurrencyResult>)? = null
    private var reset: ((CurrencyAccount) -> CompletionStage<CurrencyResult>)? = null
    private var transfer: ((CurrencyAccount, CurrencyAccount, BigDecimal) -> CompletionStage<CurrencyResult>)? = null
    private var formatter: ((BigDecimal) -> String)? = null
    private val extensions = linkedMapOf<Class<*>, Any>()

    override fun balance(operation: Function<CurrencyAccount, BigDecimal>) = apply {
        balance = { completed { operation.apply(it) } }
    }

    override fun balanceAsync(operation: Function<CurrencyAccount, CompletionStage<BigDecimal>>) = apply {
        balance = operation::apply
    }

    override fun deposit(operation: BiFunction<CurrencyAccount, BigDecimal, CurrencyResult>) = apply {
        deposit = { account, amount -> completed { operation.apply(account, amount) } }
    }

    override fun depositAsync(operation: BiFunction<CurrencyAccount, BigDecimal, CompletionStage<CurrencyResult>>) = apply {
        deposit = operation::apply
    }

    override fun withdraw(operation: BiFunction<CurrencyAccount, BigDecimal, CurrencyResult>) = apply {
        withdraw = { account, amount -> completed { operation.apply(account, amount) } }
    }

    override fun withdrawAsync(operation: BiFunction<CurrencyAccount, BigDecimal, CompletionStage<CurrencyResult>>) = apply {
        withdraw = operation::apply
    }

    override fun setBalance(operation: BiFunction<CurrencyAccount, BigDecimal, CurrencyResult>) = apply {
        setBalance = { account, amount -> completed { operation.apply(account, amount) } }
    }

    override fun reset(operation: Function<CurrencyAccount, CurrencyResult>) = apply {
        reset = { completed { operation.apply(it) } }
    }

    override fun transfer(operation: CurrencyTransferOperation) = apply {
        transfer = { from, to, amount -> completed { operation.apply(from, to, amount) } }
    }

    override fun format(operation: Function<BigDecimal, String>) = apply {
        formatter = operation::apply
    }

    override fun extension(type: Class<*>, value: Any) = apply {
        require(type.isInstance(value)) { "Extension value is not an instance of ${type.name}" }
        extensions[type] = value
    }

    /** Creates a provider and verifies that the mandatory balance operation exists. */
    fun build(descriptor: CurrencyDescriptor, name: String): CurrencyProvider {
        val balanceOperation = balance ?: error("Currency $name requires a balance operation")
        return ConfiguredCurrencyProvider(
            descriptor,
            balanceOperation,
            deposit,
            withdraw,
            setBalance,
            reset,
            transfer,
            formatter,
            extensions.toMap(),
        )
    }

    private fun <T> completed(operation: () -> T): CompletionStage<T> = try {
        CompletableFuture.completedFuture(operation())
    } catch (error: Throwable) {
        failedFuture(error)
    }
}

/** Immutable provider assembled from the operations selected by [CurrencyOperationsBuilder]. */
private class ConfiguredCurrencyProvider(
    override val descriptor: CurrencyDescriptor,
    private val balances: (CurrencyAccount) -> CompletionStage<BigDecimal>,
    private val deposits: ((CurrencyAccount, BigDecimal) -> CompletionStage<CurrencyResult>)?,
    private val withdrawals: ((CurrencyAccount, BigDecimal) -> CompletionStage<CurrencyResult>)?,
    private val mutation: ((CurrencyAccount, BigDecimal) -> CompletionStage<CurrencyResult>)?,
    private val resets: ((CurrencyAccount) -> CompletionStage<CurrencyResult>)?,
    private val transfers: ((CurrencyAccount, CurrencyAccount, BigDecimal) -> CompletionStage<CurrencyResult>)?,
    private val formatting: ((BigDecimal) -> String)?,
    private val extensions: Map<Class<*>, Any>,
) : CurrencyProvider, CurrencyDeposits, CurrencyWithdrawals, CurrencyBalanceMutation,
    CurrencyReset, CurrencyTransfers, CurrencyFormatting, CurrencyCapabilitySource {

    override val declaredCapabilities = buildSet {
        add(CurrencyCapability.BALANCE)
        if (deposits != null) add(CurrencyCapability.DEPOSIT)
        if (withdrawals != null) add(CurrencyCapability.WITHDRAW)
        if (mutation != null) add(CurrencyCapability.SET_BALANCE)
        if (resets != null) add(CurrencyCapability.RESET)
        if (transfers != null) add(CurrencyCapability.TRANSFER)
        if (formatting != null) add(CurrencyCapability.FORMATTING)
    }

    override fun balance(account: CurrencyAccount) = balances(account)
    override fun deposit(account: CurrencyAccount, amount: BigDecimal) = deposits?.invoke(account, amount) ?: unsupported()
    override fun withdraw(account: CurrencyAccount, amount: BigDecimal) = withdrawals?.invoke(account, amount) ?: unsupported()
    override fun setBalance(account: CurrencyAccount, amount: BigDecimal) = mutation?.invoke(account, amount) ?: unsupported()
    override fun reset(account: CurrencyAccount) = resets?.invoke(account) ?: unsupported()
    override fun transfer(from: CurrencyAccount, to: CurrencyAccount, amount: BigDecimal) =
        transfers?.invoke(from, to, amount) ?: unsupported()

    override fun format(amount: BigDecimal) =
        formatting?.invoke(amount) ?: descriptor.normalize(amount).toPlainString() + descriptor.symbol

    override fun <T : Any> extension(type: Class<T>): T? = extensions[type]?.let(type::cast)

    private fun unsupported() =
        CompletableFuture.completedFuture(CurrencyResult.unsupported("Operation is not configured"))
}

/** Internal capability declaration used when a provider implements optional interfaces structurally. */
internal interface CurrencyCapabilitySource {
    val declaredCapabilities: Set<CurrencyCapability>
}

private fun <T> failedFuture(error: Throwable): CompletionStage<T> = CompletableFuture<T>().also {
    it.completeExceptionally(IllegalStateException("Currency operation failed", error))
}
