package ru.privatenull.pnlibrary.api.currency

import ru.privatenull.pnlibrary.api.plugin.PluginId
import ru.privatenull.pnlibrary.api.placeholders.PlaceholderAccess
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.CompletionStage
import java.util.function.BiFunction
import java.util.function.Consumer
import java.util.function.Function

data class CurrencyKey(val owner: PluginId, val name: String) {
    init { require(name.matches(Regex("[a-z0-9_.-]+"))) { "Invalid currency name: $name" } }
    override fun toString() = "${owner.value}:$name"
}

interface Currency {
    val key: CurrencyKey
    val descriptor: CurrencyDescriptor
    val capabilities: Set<CurrencyCapability>
    fun supports(capability: CurrencyCapability) = capability in capabilities
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
    fun format(amount: BigDecimal): String
    fun <T : Any> extension(type: Class<T>): T?
}

enum class CurrencyRegistrationState { ACTIVE, DISABLED, CLOSED }

interface CurrencyRegistration : Currency, AutoCloseable {
    val state: CurrencyRegistrationState
    fun enable()
    fun disable()
}

interface CurrencyRegistrationOptions {
    fun access(access: CurrencyAccess): CurrencyRegistrationOptions
    fun access(configure: Consumer<CurrencyAccess.Builder>): CurrencyRegistrationOptions
    fun aliases(vararg aliases: String): CurrencyRegistrationOptions
}

interface CurrencyOperations {
    fun balance(operation: Function<CurrencyAccount, BigDecimal>): CurrencyOperations
    fun balanceAsync(operation: Function<CurrencyAccount, CompletionStage<BigDecimal>>): CurrencyOperations
    fun deposit(operation: BiFunction<CurrencyAccount, BigDecimal, CurrencyResult>): CurrencyOperations
    fun depositAsync(operation: BiFunction<CurrencyAccount, BigDecimal, CompletionStage<CurrencyResult>>): CurrencyOperations
    fun withdraw(operation: BiFunction<CurrencyAccount, BigDecimal, CurrencyResult>): CurrencyOperations
    fun withdrawAsync(operation: BiFunction<CurrencyAccount, BigDecimal, CompletionStage<CurrencyResult>>): CurrencyOperations
    fun setBalance(operation: BiFunction<CurrencyAccount, BigDecimal, CurrencyResult>): CurrencyOperations
    fun reset(operation: Function<CurrencyAccount, CurrencyResult>): CurrencyOperations
    fun transfer(operation: CurrencyTransferOperation): CurrencyOperations
    fun format(operation: Function<BigDecimal, String>): CurrencyOperations
    fun extension(type: Class<*>, value: Any): CurrencyOperations
}

fun interface CurrencyTransferOperation {
    fun apply(from: CurrencyAccount, to: CurrencyAccount, amount: BigDecimal): CurrencyResult
}

interface CurrencyDefinitionBuilder : CurrencyRegistrationOptions {
    fun descriptor(configure: Consumer<CurrencyDescriptor.Builder>): CurrencyDefinitionBuilder
    fun operations(configure: Consumer<CurrencyOperations>): CurrencyDefinitionBuilder
}

interface ManagedCurrencyBuilder : CurrencyRegistrationOptions {
    fun descriptor(configure: Consumer<CurrencyDescriptor.Builder>): ManagedCurrencyBuilder
    /** Uses shared storage without taking ownership of its lifecycle. */
    fun storage(storage: CurrencyStorage): ManagedCurrencyBuilder
    /** Uses storage owned and closed by this currency registration. */
    fun ownedStorage(storage: CurrencyStorage): ManagedCurrencyBuilder
    fun service(name: String): ManagedCurrencyBuilder
    fun placeholders(configure: Consumer<CurrencyPlaceholderOptions>): ManagedCurrencyBuilder
    fun commands(configure: Consumer<CurrencyCommandOptions>): ManagedCurrencyBuilder
}

interface CurrencyPlaceholderOptions {
    fun enabled(value: Boolean): CurrencyPlaceholderOptions
    fun access(access: PlaceholderAccess): CurrencyPlaceholderOptions
    fun placeholderApi(enabled: Boolean): CurrencyPlaceholderOptions
    fun placeholderApi(namespace: String): CurrencyPlaceholderOptions
}

data class CurrencyCommandSettings(
    val enabled: Boolean = true,
    val permissionPrefix: String? = null,
    val prefix: String = "§8[§6{currency}§8] ",
    val success: String = "§aOperation completed: {amount}",
    val failure: String = "§c{error}",
    val balance: String = "§fBalance: §a{balance}",
    val historyEmpty: String = "§7No transactions found.",
    val confirmationMode: CurrencyConfirmationMode = CurrencyConfirmationMode.CONSOLE,
    val confirmationTimeoutSeconds: Int = 60,
) {
    init {
        require(confirmationTimeoutSeconds in 10..600) {
            "Currency confirmation timeout must be between 10 and 600 seconds"
        }
    }
}

/** Determines whether destructive administrative commands require a second approval step. */
enum class CurrencyConfirmationMode { NONE, CONSOLE }

interface CurrencyCommandOptions {
    fun enabled(value: Boolean): CurrencyCommandOptions
    fun permissionPrefix(value: String): CurrencyCommandOptions
    fun prefix(value: String): CurrencyCommandOptions
    fun success(value: String): CurrencyCommandOptions
    fun failure(value: String): CurrencyCommandOptions
    fun balance(value: String): CurrencyCommandOptions
    fun historyEmpty(value: String): CurrencyCommandOptions
    fun confirmation(mode: CurrencyConfirmationMode): CurrencyCommandOptions
    fun confirmationTimeoutSeconds(value: Int): CurrencyCommandOptions
}

interface CurrencyService : AutoCloseable {
    /** Defines and registers a lightweight currency in one call. */
    fun register(name: String, configure: Consumer<CurrencyDefinitionBuilder>): CurrencyRegistration

    /** Registers a storage-backed currency with atomic transactions and queryable history. */
    fun managed(name: String, configure: Consumer<ManagedCurrencyBuilder>): CurrencyRegistration

    /** Registers a reusable provider class. */
    fun register(name: String, provider: CurrencyProvider): CurrencyRegistration = register(name, provider, Consumer { })
    fun register(name: String, provider: CurrencyProvider, configure: Consumer<CurrencyRegistrationOptions>): CurrencyRegistration

    fun get(reference: String): Currency?
    fun require(reference: String): Currency = get(reference) ?: error("Currency $reference is unavailable or inaccessible")
    fun all(): List<Currency>
    fun unregister(name: String)
    override fun close()
}

/** Restricted registry for platform integrations; consumer plugins use their owned [CurrencyService]. */
interface CurrencyProviderRegistry {
    fun register(owner: PluginId, name: String, provider: CurrencyProvider): CurrencyRegistration = register(owner, name, provider, CurrencyAccess.shared())
    fun register(owner: PluginId, name: String, provider: CurrencyProvider, access: CurrencyAccess): CurrencyRegistration
    fun get(reference: String): Currency?
    fun all(): List<Currency>
}
