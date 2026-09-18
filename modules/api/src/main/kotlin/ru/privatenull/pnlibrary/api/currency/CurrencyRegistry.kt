package ru.privatenull.pnlibrary.api.currency

import ru.privatenull.pnlibrary.api.plugin.PluginId
import ru.privatenull.pnlibrary.api.placeholders.PlaceholderAccess
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.CompletionStage
import java.util.function.BiFunction
import java.util.function.Consumer
import java.util.function.Function

/**
 * Globally unique currency identity composed of its owning plugin and local name.
 *
 * @property owner plugin that controls the registration lifecycle
 * @property name normalized lowercase local name using letters, digits, `_`, `.`, or `-`
 */
data class CurrencyKey(val owner: PluginId, val name: String) {
    init { require(name.matches(Regex("[a-z0-9_.-]+"))) { "Invalid currency name: $name" } }
    /** Returns the canonical `owner:name` reference accepted by [CurrencyService.get]. */
    override fun toString(): String = "${owner.value}:$name"
}

/**
 * Consumer-facing asynchronous facade for one registered currency.
 *
 * Amount mutations reject values that violate [descriptor] precision. Deposit,
 * withdrawal, and transfer require positive amounts; set-balance accepts zero.
 * Unsupported or inactive mutations complete normally with a structured [CurrencyResult].
 */
interface Currency {
    /** Globally unique registration key. */
    val key: CurrencyKey
    /** Presentation and decimal rules applied by this facade. */
    val descriptor: CurrencyDescriptor
    /** Immutable set of operations supported by the underlying provider. */
    val capabilities: Set<CurrencyCapability>
    /** Returns whether the provider exposes [capability]. */
    fun supports(capability: CurrencyCapability) = capability in capabilities
    /** Returns a normalized balance for [playerId]. */
    fun balance(playerId: UUID): CompletionStage<BigDecimal>
    /** Returns whether the normalized balance covers positive [amount]. */
    fun has(playerId: UUID, amount: BigDecimal): CompletionStage<Boolean>
    /** Attempts to add positive [amount] to the player's balance. */
    fun deposit(playerId: UUID, amount: BigDecimal): CompletionStage<CurrencyResult>
    /** Long convenience overload for [deposit]. */
    fun deposit(playerId: UUID, amount: Long): CompletionStage<CurrencyResult> = deposit(playerId, BigDecimal.valueOf(amount))
    /** Attempts to subtract positive [amount] from the player's balance. */
    fun withdraw(playerId: UUID, amount: BigDecimal): CompletionStage<CurrencyResult>
    /** Long convenience overload for [withdraw]. */
    fun withdraw(playerId: UUID, amount: Long): CompletionStage<CurrencyResult> = withdraw(playerId, BigDecimal.valueOf(amount))
    /** Attempts to replace the player's balance with non-negative [amount]. */
    fun setBalance(playerId: UUID, amount: BigDecimal): CompletionStage<CurrencyResult>
    /** Long convenience overload for [setBalance]. */
    fun setBalance(playerId: UUID, amount: Long): CompletionStage<CurrencyResult> = setBalance(playerId, BigDecimal.valueOf(amount))
    /** Restores the player's balance through the provider reset capability. */
    fun reset(playerId: UUID): CompletionStage<CurrencyResult>
    /** Attempts to atomically transfer positive [amount] between distinct players. */
    fun transfer(from: UUID, to: UUID, amount: BigDecimal): CompletionStage<CurrencyResult>
    /** Long convenience overload for [transfer]. */
    fun transfer(from: UUID, to: UUID, amount: Long): CompletionStage<CurrencyResult> = transfer(from, to, BigDecimal.valueOf(amount))
    /** Formats normalized [amount] using provider formatting or descriptor defaults. */
    fun format(amount: BigDecimal): String
    /** Returns an optional advanced contract registered under exact [type]. */
    fun <T : Any> extension(type: Class<T>): T?
}

/** Lifecycle state of a currency registration. */
enum class CurrencyRegistrationState {
    /** Visible and available for operations. */ ACTIVE,
    /** Temporarily unavailable but eligible for [CurrencyRegistration.enable]. */ DISABLED,
    /** Permanently removed and no longer eligible for re-enabling. */ CLOSED,
}

/** Live currency registration that owns its provider and attached integrations. */
interface CurrencyRegistration : Currency, AutoCloseable {
    /** Current registration lifecycle state. */
    val state: CurrencyRegistrationState
    /** Makes a disabled registration visible and operational again. */
    fun enable()
    /** Temporarily hides the currency and makes mutations return unavailable results. */
    fun disable()
}

/** Options shared by lightweight, managed, and direct provider registrations. */
interface CurrencyRegistrationOptions {
    /** Replaces the complete consumer access policy. */
    fun access(access: CurrencyAccess): CurrencyRegistrationOptions
    /** Builds and replaces the access policy through a Java-friendly callback. */
    fun access(configure: Consumer<CurrencyAccess.Builder>): CurrencyRegistrationOptions
    /** Adds local aliases owned by the same plugin; conflicts fail during registration. */
    fun aliases(vararg aliases: String): CurrencyRegistrationOptions
}

/** Java-friendly operation builder for a lightweight currency provider. */
interface CurrencyOperations {
    /** Configures the mandatory synchronous balance lookup. */
    fun balance(operation: Function<CurrencyAccount, BigDecimal>): CurrencyOperations
    /** Configures the mandatory asynchronous balance lookup. */
    fun balanceAsync(operation: Function<CurrencyAccount, CompletionStage<BigDecimal>>): CurrencyOperations
    /** Configures synchronous deposit support. */
    fun deposit(operation: BiFunction<CurrencyAccount, BigDecimal, CurrencyResult>): CurrencyOperations
    /** Configures asynchronous deposit support. */
    fun depositAsync(operation: BiFunction<CurrencyAccount, BigDecimal, CompletionStage<CurrencyResult>>): CurrencyOperations
    /** Configures synchronous withdrawal support. */
    fun withdraw(operation: BiFunction<CurrencyAccount, BigDecimal, CurrencyResult>): CurrencyOperations
    /** Configures asynchronous withdrawal support. */
    fun withdrawAsync(operation: BiFunction<CurrencyAccount, BigDecimal, CompletionStage<CurrencyResult>>): CurrencyOperations
    /** Configures synchronous set-balance support. */
    fun setBalance(operation: BiFunction<CurrencyAccount, BigDecimal, CurrencyResult>): CurrencyOperations
    /** Configures synchronous reset support. */
    fun reset(operation: Function<CurrencyAccount, CurrencyResult>): CurrencyOperations
    /** Configures synchronous atomic transfer support. */
    fun transfer(operation: CurrencyTransferOperation): CurrencyOperations
    /** Configures provider-specific amount presentation. */
    fun format(operation: Function<BigDecimal, String>): CurrencyOperations
    /** Publishes [value] as an advanced extension under exact [type]. */
    fun extension(type: Class<*>, value: Any): CurrencyOperations
}

/** Synchronous operation that atomically transfers one amount between accounts. */
fun interface CurrencyTransferOperation {
    /** Applies a transfer and returns its structured result. */
    fun apply(from: CurrencyAccount, to: CurrencyAccount, amount: BigDecimal): CurrencyResult
}

/** Definition builder for a lightweight currency backed by caller-supplied operations. */
interface CurrencyDefinitionBuilder : CurrencyRegistrationOptions {
    /** Configures display and decimal rules. */
    fun descriptor(configure: Consumer<CurrencyDescriptor.Builder>): CurrencyDefinitionBuilder
    /** Configures mandatory balance lookup and optional mutation capabilities. */
    fun operations(configure: Consumer<CurrencyOperations>): CurrencyDefinitionBuilder
}

/**
 * Definition builder for a storage-backed currency with an auditable ledger.
 *
 * Exactly one storage method must be called before registration. Use [storage]
 * when several currencies share a store whose lifecycle is managed elsewhere,
 * or [ownedStorage] when this registration should close it automatically.
 */
interface ManagedCurrencyBuilder : CurrencyRegistrationOptions {
    /** Configures display and decimal rules. */
    fun descriptor(configure: Consumer<CurrencyDescriptor.Builder>): ManagedCurrencyBuilder
    /** Uses shared storage without taking ownership of its lifecycle. */
    fun storage(storage: CurrencyStorage): ManagedCurrencyBuilder
    /** Uses storage owned and closed by this currency registration. */
    fun ownedStorage(storage: CurrencyStorage): ManagedCurrencyBuilder
    /** Sets the nonblank service identifier written to API-generated ledger entries. */
    fun service(name: String): ManagedCurrencyBuilder
    /** Configures generated balance, formatted-value, and symbol placeholders. */
    fun placeholders(configure: Consumer<CurrencyPlaceholderOptions>): ManagedCurrencyBuilder
    /** Configures platform command presentation and confirmation behavior. */
    fun commands(configure: Consumer<CurrencyCommandOptions>): ManagedCurrencyBuilder
}

/** Generated placeholder publication options for a managed currency. */
interface CurrencyPlaceholderOptions {
    /** Enables or disables generation of library placeholders. */
    fun enabled(value: Boolean): CurrencyPlaceholderOptions
    /** Sets which plugins may consume generated placeholders. */
    fun access(access: PlaceholderAccess): CurrencyPlaceholderOptions
    /** Enables or disables publication through the PlaceholderAPI adapter. */
    fun placeholderApi(enabled: Boolean): CurrencyPlaceholderOptions
    /** Enables PlaceholderAPI publication with explicit [namespace]. */
    fun placeholderApi(namespace: String): CurrencyPlaceholderOptions
}

/**
 * Immutable command presentation and destructive-operation confirmation settings.
 *
 * Message strings may contain legacy `&` or `§` color codes and documented brace tokens
 * such as `{currency}`, `{amount}`, `{balance}`, and `{error}`. Exact command availability
 * and rendering remain platform-specific.
 *
 * @property enabled whether platform command integration is exposed
 * @property permissionPrefix custom permission root, or `null` for the generated default
 * @property prefix component text prepended to command responses
 * @property success template for successful mutations
 * @property failure template for failed or rejected operations
 * @property balance template for balance lookup
 * @property historyEmpty response used when a ledger query returns no rows
 * @property confirmationMode second-step policy for destructive administration
 * @property confirmationTimeoutSeconds confirmation lifetime from 10 through 600 seconds
 */
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
enum class CurrencyConfirmationMode {
    /** Executes destructive operations immediately after permission checks. */ NONE,
    /** Requires a time-limited approval through the server console. */ CONSOLE,
}

/** Mutable command integration options exposed by [ManagedCurrencyBuilder.commands]. */
interface CurrencyCommandOptions {
    /** Enables or disables command integration. */
    fun enabled(value: Boolean): CurrencyCommandOptions
    /** Sets the permission root; blank values restore the generated default. */
    fun permissionPrefix(value: String): CurrencyCommandOptions
    /** Sets the response prefix component template. */
    fun prefix(value: String): CurrencyCommandOptions
    /** Sets the successful-operation template. */
    fun success(value: String): CurrencyCommandOptions
    /** Sets the rejected or failed operation template. */
    fun failure(value: String): CurrencyCommandOptions
    /** Sets the balance response template. */
    fun balance(value: String): CurrencyCommandOptions
    /** Sets the empty-history response. */
    fun historyEmpty(value: String): CurrencyCommandOptions
    /** Sets the destructive-operation confirmation policy. */
    fun confirmation(mode: CurrencyConfirmationMode): CurrencyCommandOptions
    /** Sets confirmation lifetime from 10 through 600 seconds. */
    fun confirmationTimeoutSeconds(value: Int): CurrencyCommandOptions
}

/**
 * Plugin-owned facade for registering and discovering currencies.
 *
 * Unqualified references resolve inside the owning plugin. Cross-plugin references use
 * canonical `owner:name` form and are filtered through [CurrencyAccess]. Closing the
 * service closes every registration created through it.
 */
interface CurrencyService : AutoCloseable {
    /** Defines and registers a lightweight currency in one call. */
    fun register(name: String, configure: Consumer<CurrencyDefinitionBuilder>): CurrencyRegistration

    /** Registers a storage-backed currency with atomic transactions and queryable history. */
    fun managed(name: String, configure: Consumer<ManagedCurrencyBuilder>): CurrencyRegistration

    /** Registers a reusable provider class. */
    fun register(name: String, provider: CurrencyProvider): CurrencyRegistration = register(name, provider, Consumer { })
    /** Registers a reusable [provider] with aliases or an explicit access policy. */
    fun register(name: String, provider: CurrencyProvider, configure: Consumer<CurrencyRegistrationOptions>): CurrencyRegistration

    /** Resolves an accessible local name, alias, or canonical `owner:name` reference. */
    fun get(reference: String): Currency?
    /** Resolves [reference] or fails when missing, disabled, closed, or inaccessible. */
    fun require(reference: String): Currency = get(reference) ?: error("Currency $reference is unavailable or inaccessible")
    /** Returns all active currencies visible to this plugin in canonical-key order. */
    fun all(): List<Currency>
    /** Closes a currency owned by this plugin, addressed by name or alias. */
    fun unregister(name: String)
    /** Closes every registration owned by this service. Safe to repeat. */
    override fun close()
}

/** Restricted registry for platform integrations; consumer plugins use their owned [CurrencyService]. */
interface CurrencyProviderRegistry {
    /** Registers a provider shared with every pnLibrary plugin. */
    fun register(owner: PluginId, name: String, provider: CurrencyProvider): CurrencyRegistration = register(owner, name, provider, CurrencyAccess.shared())
    /** Registers a provider with explicit cross-plugin [access]. */
    fun register(owner: PluginId, name: String, provider: CurrencyProvider, access: CurrencyAccess): CurrencyRegistration
    /** Resolves an active canonical `owner:name` reference without consumer filtering. */
    fun get(reference: String): Currency?
    /** Returns every active provider registration in canonical-key order. */
    fun all(): List<Currency>
}
