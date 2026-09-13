package ru.privatenull.pnlibrary.api.currency

import ru.privatenull.pnlibrary.api.plugin.PluginId
import java.math.BigDecimal
import java.util.concurrent.CompletionStage
import java.util.concurrent.CompletableFuture
import java.util.UUID
import java.util.function.BiFunction
import java.util.function.Consumer
import java.util.function.Function

interface CurrencyProvider {
    val definition: CurrencyDefinition
    val capabilities: Set<CurrencyCapability>
    fun balance(account: CurrencyAccount): CompletionStage<BigDecimal>
    fun deposit(account: CurrencyAccount, amount: BigDecimal): CompletionStage<CurrencyResult> = unsupported(CurrencyCapability.DEPOSIT)
    fun withdraw(account: CurrencyAccount, amount: BigDecimal): CompletionStage<CurrencyResult> = unsupported(CurrencyCapability.WITHDRAW)
    fun setBalance(account: CurrencyAccount, amount: BigDecimal): CompletionStage<CurrencyResult> = unsupported(CurrencyCapability.SET_BALANCE)
    fun reset(account: CurrencyAccount): CompletionStage<CurrencyResult> = unsupported(CurrencyCapability.RESET)
    fun transfer(from: CurrencyAccount, to: CurrencyAccount, amount: BigDecimal): CompletionStage<CurrencyResult> = unsupported(CurrencyCapability.TRANSFER)
    fun format(amount: BigDecimal): String = "${definition.normalize(amount).toPlainString()}${definition.symbol}"
    fun <T : Any> extension(type: Class<T>): T? = null

    private fun unsupported(capability: CurrencyCapability): CompletionStage<CurrencyResult> =
        CompletableFuture.completedFuture(CurrencyResult.unsupported("Currency does not support $capability"))
}

interface CurrencyRegistration : AutoCloseable {
    val key: CurrencyKey
    val provider: CurrencyProvider
    val isEnabled: Boolean
    val definition: CurrencyDefinition get() = provider.definition
    val capabilities: Set<CurrencyCapability> get() = provider.capabilities
    fun supports(capability: CurrencyCapability): Boolean = capability in capabilities
    fun balance(playerId: UUID): CompletionStage<BigDecimal>
    fun has(playerId: UUID, amount: BigDecimal): CompletionStage<Boolean>
    fun deposit(playerId: UUID, amount: BigDecimal): CompletionStage<CurrencyResult>
    fun deposit(playerId: UUID, amount: Long): CompletionStage<CurrencyResult> = deposit(playerId, BigDecimal.valueOf(amount))
    fun withdraw(playerId: UUID, amount: BigDecimal): CompletionStage<CurrencyResult>
    fun withdraw(playerId: UUID, amount: Long): CompletionStage<CurrencyResult> = withdraw(playerId, BigDecimal.valueOf(amount))
    fun setBalance(playerId: UUID, amount: BigDecimal): CompletionStage<CurrencyResult>
    fun setBalance(playerId: UUID, amount: Long): CompletionStage<CurrencyResult> = setBalance(playerId, BigDecimal.valueOf(amount))
    fun reset(playerId: UUID): CompletionStage<CurrencyResult>
    fun transfer(from: UUID, to: UUID, amount: BigDecimal): CompletionStage<CurrencyResult>
    fun transfer(from: UUID, to: UUID, amount: Long): CompletionStage<CurrencyResult> = transfer(from, to, BigDecimal.valueOf(amount))
    fun format(amount: BigDecimal): String = provider.format(amount)
    fun <T : Any> extension(type: Class<T>): T? = provider.extension(type)
    fun enable()
    fun disable()
}

interface CurrencyBuilder {
    fun displayName(value: String): CurrencyBuilder
    fun symbol(value: String): CurrencyBuilder
    fun fractionDigits(value: Int): CurrencyBuilder
    fun access(value: CurrencyAccess): CurrencyBuilder
    fun access(configure: Consumer<CurrencyAccess.Builder>): CurrencyBuilder
    fun balance(operation: Function<CurrencyAccount, BigDecimal>): CurrencyBuilder
    fun balanceAsync(operation: Function<CurrencyAccount, CompletionStage<BigDecimal>>): CurrencyBuilder
    fun deposit(operation: BiFunction<CurrencyAccount, BigDecimal, CurrencyResult>): CurrencyBuilder
    fun depositAsync(operation: BiFunction<CurrencyAccount, BigDecimal, CompletionStage<CurrencyResult>>): CurrencyBuilder
    fun withdraw(operation: BiFunction<CurrencyAccount, BigDecimal, CurrencyResult>): CurrencyBuilder
    fun withdrawAsync(operation: BiFunction<CurrencyAccount, BigDecimal, CompletionStage<CurrencyResult>>): CurrencyBuilder
    fun setBalance(operation: BiFunction<CurrencyAccount, BigDecimal, CurrencyResult>): CurrencyBuilder
    fun reset(operation: Function<CurrencyAccount, CurrencyResult>): CurrencyBuilder
    fun transfer(operation: TransferOperation): CurrencyBuilder
    fun formatter(operation: Function<BigDecimal, String>): CurrencyBuilder
    fun extension(type: Class<*>, value: Any): CurrencyBuilder
    fun register(): CurrencyRegistration
}

fun interface TransferOperation {
    fun apply(from: CurrencyAccount, to: CurrencyAccount, amount: BigDecimal): CurrencyResult
}

interface CurrencyService : AutoCloseable {
    /** Starts concise lambda-based registration. */
    fun currency(name: String): CurrencyBuilder

    /** Registers a full provider class. */
    fun register(name: String, provider: CurrencyProvider): CurrencyRegistration =
        register(name, provider, CurrencyAccess.ownerOnly())
    fun register(name: String, provider: CurrencyProvider, access: CurrencyAccess): CurrencyRegistration

    fun get(reference: String): CurrencyRegistration?
    fun require(reference: String): CurrencyRegistration = get(reference)
        ?: error("Currency $reference is unavailable or inaccessible")
    fun all(): List<CurrencyRegistration>
    fun unregister(name: String)
}

/** Runtime bridge registry used by platform modules such as Vault and PlayerPoints adapters. */
interface CurrencyProviderRegistry {
    fun register(owner: PluginId, name: String, provider: CurrencyProvider): CurrencyRegistration =
        register(owner, name, provider, CurrencyAccess.shared())
    fun register(owner: PluginId, name: String, provider: CurrencyProvider, access: CurrencyAccess): CurrencyRegistration
}
