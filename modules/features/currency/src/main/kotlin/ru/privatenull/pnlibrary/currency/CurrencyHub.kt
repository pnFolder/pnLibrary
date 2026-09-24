package ru.privatenull.pnlibrary.currency

import ru.privatenull.pnlibrary.api.currency.Currency
import ru.privatenull.pnlibrary.api.currency.CurrencyAccess
import ru.privatenull.pnlibrary.api.currency.CurrencyAccount
import ru.privatenull.pnlibrary.api.currency.CurrencyBalanceMutation
import ru.privatenull.pnlibrary.api.currency.CurrencyCapability
import ru.privatenull.pnlibrary.api.currency.CurrencyDefinitionBuilder
import ru.privatenull.pnlibrary.api.currency.CurrencyDeposits
import ru.privatenull.pnlibrary.api.currency.CurrencyFormatting
import ru.privatenull.pnlibrary.api.currency.CurrencyKey
import ru.privatenull.pnlibrary.api.currency.CurrencyProvider
import ru.privatenull.pnlibrary.api.currency.CurrencyProviderRegistry
import ru.privatenull.pnlibrary.api.currency.CurrencyRegistration
import ru.privatenull.pnlibrary.api.currency.CurrencyRegistrationOptions
import ru.privatenull.pnlibrary.api.currency.CurrencyRegistrationState
import ru.privatenull.pnlibrary.api.currency.CurrencyRejectReason
import ru.privatenull.pnlibrary.api.currency.CurrencyReset
import ru.privatenull.pnlibrary.api.currency.CurrencyResult
import ru.privatenull.pnlibrary.api.currency.CurrencyService
import ru.privatenull.pnlibrary.api.currency.CurrencyTransfers
import ru.privatenull.pnlibrary.api.currency.CurrencyWithdrawals
import ru.privatenull.pnlibrary.api.currency.ManagedCurrencyBuilder
import ru.privatenull.pnlibrary.api.plugin.PluginId
import ru.privatenull.pnlibrary.api.placeholders.PlaceholderService
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer

/** Thread-safe registry coordinating currency ownership, visibility, aliases, and lifecycle. */
internal class CurrencyHub : CurrencyProviderRegistry, AutoCloseable {
    private val entries = ConcurrentHashMap<CurrencyKey, Entry>()
    private val aliases = ConcurrentHashMap<Pair<PluginId, String>, CurrencyKey>()
    private val closed = AtomicBoolean(false)

    fun scope(owner: PluginId, placeholders: PlaceholderService): CurrencyService = Scope(owner, placeholders)

    override fun register(owner: PluginId, name: String, provider: CurrencyProvider, access: CurrencyAccess): CurrencyRegistration =
        install(owner, name, provider, access, emptySet())

    override fun get(reference: String): Currency? {
        val separator = reference.indexOf(':')
        if (separator <= 0) return null
        val requested = CurrencyKey(
            PluginId.of(reference.substring(0, separator)),
            normalizeCurrencyName(reference.substring(separator + 1)),
        )
        return entries[aliases[requested.owner to requested.name] ?: requested]
            ?.takeIf { it.state == CurrencyRegistrationState.ACTIVE }
    }

    override fun all(): List<Currency> = entries.values
        .filter { it.state == CurrencyRegistrationState.ACTIVE }
        .sortedBy { it.key.toString() }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        entries.values.toList().forEach(Entry::close)
        entries.clear()
        aliases.clear()
    }

    private fun install(
        owner: PluginId,
        name: String,
        provider: CurrencyProvider,
        access: CurrencyAccess,
        requestedAliases: Set<String>,
    ): CurrencyRegistration {
        check(!closed.get()) { "Currency registry is closed" }
        val key = CurrencyKey(owner, normalizeCurrencyName(name))
        val normalizedAliases = requestedAliases.map(::normalizeCurrencyName).toSet()
        val entry = Entry(key, provider, access, normalizedAliases)
        synchronized(entries) {
            require(!entries.containsKey(key)) { "Currency $key is already registered" }
            normalizedAliases.forEach { alias ->
                require(!aliases.containsKey(owner to alias) && !entries.containsKey(CurrencyKey(owner, alias))) {
                    "Currency alias $owner:$alias is already registered"
                }
            }
            entries[key] = entry
            normalizedAliases.forEach { aliases[owner to it] = key }
        }
        return entry
    }

    private inner class Scope(private val owner: PluginId, private val placeholders: PlaceholderService) : CurrencyService {
        private val owned = ConcurrentHashMap.newKeySet<CurrencyKey>()
        private val scopeClosed = AtomicBoolean(false)

        override fun register(name: String, configure: Consumer<CurrencyDefinitionBuilder>): CurrencyRegistration {
            checkOpen()
            val builder = CurrencyDefinition(name)
            configure.accept(builder)
            return own(install(owner, name, builder.provider(), builder.access, builder.aliases))
        }

        override fun managed(name: String, configure: Consumer<ManagedCurrencyBuilder>): CurrencyRegistration {
            checkOpen()
            val builder = ManagedCurrencyDefinition(owner, name)
            configure.accept(builder)
            val registration = own(install(owner, name, builder.provider(), builder.access, builder.aliases))
            builder.installPlaceholders(registration, placeholders).forEach { (registration as Entry).attach(it) }
            return registration
        }

        override fun register(name: String, provider: CurrencyProvider, configure: Consumer<CurrencyRegistrationOptions>): CurrencyRegistration {
            checkOpen()
            val options = CurrencyOptions()
            configure.accept(options)
            return own(install(owner, name, provider, options.access, options.aliases))
        }

        override fun get(reference: String): Currency? {
            val requested = parseReference(owner, reference)
            val key = aliases[requested.owner to requested.name] ?: requested
            return entries[key]?.takeIf {
                it.state == CurrencyRegistrationState.ACTIVE && it.access.allows(it.key.owner, owner)
            }
        }

        override fun all(): List<Currency> = entries.values
            .filter { it.state == CurrencyRegistrationState.ACTIVE && it.access.allows(it.key.owner, owner) }
            .sortedBy { it.key.toString() }

        override fun unregister(name: String) {
            val requested = CurrencyKey(owner, normalizeCurrencyName(name))
            entries[aliases[owner to requested.name] ?: requested]?.takeIf { it.key.owner == owner }?.close()
        }

        override fun close() {
            if (!scopeClosed.compareAndSet(false, true)) return
            owned.toList().forEach { entries[it]?.close() }
            owned.clear()
        }

        private fun own(registration: CurrencyRegistration) = registration.also { owned += it.key }
        private fun checkOpen() = check(!scopeClosed.get()) { "Currencies for $owner are closed" }
    }

    private inner class Entry(
        override val key: CurrencyKey,
        private val provider: CurrencyProvider,
        val access: CurrencyAccess,
        private val registeredAliases: Set<String>,
    ) : CurrencyRegistration {
        private val attachments = java.util.Collections.synchronizedList(mutableListOf<AutoCloseable>())
        private val enabled = AtomicBoolean(true)
        private val entryClosed = AtomicBoolean(false)
        override val descriptor get() = provider.descriptor
        override val state get() = when {
            entryClosed.get() -> CurrencyRegistrationState.CLOSED
            enabled.get() -> CurrencyRegistrationState.ACTIVE
            else -> CurrencyRegistrationState.DISABLED
        }
        override val capabilities = if (provider is CurrencyCapabilitySource) provider.declaredCapabilities else buildSet {
            add(CurrencyCapability.BALANCE)
            if (provider is CurrencyDeposits) add(CurrencyCapability.DEPOSIT)
            if (provider is CurrencyWithdrawals) add(CurrencyCapability.WITHDRAW)
            if (provider is CurrencyBalanceMutation) add(CurrencyCapability.SET_BALANCE)
            if (provider is CurrencyReset) add(CurrencyCapability.RESET)
            if (provider is CurrencyTransfers) add(CurrencyCapability.TRANSFER)
            if (provider is CurrencyFormatting) add(CurrencyCapability.FORMATTING)
        }

        override fun balance(playerId: UUID): CompletionStage<BigDecimal> = availableValue {
            provider.balance(CurrencyAccount(playerId)).thenApply(descriptor::normalize)
        }
        override fun has(playerId: UUID, amount: BigDecimal): CompletionStage<Boolean> {
            if (!valid(amount, true)) return CompletableFuture.completedFuture(false)
            return balance(playerId).thenApply { it >= descriptor.normalize(amount) }
        }
        override fun deposit(playerId: UUID, amount: BigDecimal) = amountOperation(CurrencyCapability.DEPOSIT, amount) {
            (provider as CurrencyDeposits).deposit(CurrencyAccount(playerId), it)
        }
        override fun withdraw(playerId: UUID, amount: BigDecimal) = amountOperation(CurrencyCapability.WITHDRAW, amount) {
            (provider as CurrencyWithdrawals).withdraw(CurrencyAccount(playerId), it)
        }
        override fun setBalance(playerId: UUID, amount: BigDecimal) = amountOperation(CurrencyCapability.SET_BALANCE, amount, true) {
            (provider as CurrencyBalanceMutation).setBalance(CurrencyAccount(playerId), it)
        }
        override fun reset(playerId: UUID) = resultOperation(CurrencyCapability.RESET) {
            (provider as CurrencyReset).reset(CurrencyAccount(playerId))
        }
        override fun transfer(from: UUID, to: UUID, amount: BigDecimal) = amountOperation(CurrencyCapability.TRANSFER, amount) {
            (provider as CurrencyTransfers).transfer(CurrencyAccount(from), CurrencyAccount(to), it)
        }
        override fun format(amount: BigDecimal) = if (provider is CurrencyFormatting) provider.format(descriptor.normalize(amount)) else descriptor.normalize(amount).toPlainString() + descriptor.symbol
        override fun <T : Any> extension(type: Class<T>): T? = provider.extension(type)
        override fun enable() {
            check(!entryClosed.get()) { "Currency $key is closed" }
            enabled.set(true)
        }

        override fun disable() {
            enabled.set(false)
        }
        override fun close() {
            if (!entryClosed.compareAndSet(false, true)) return
            enabled.set(false)
            synchronized(entries) {
                entries.remove(key, this)
                registeredAliases.forEach { aliases.remove(key.owner to it, key) }
            }
            attachments.toList().forEach { runCatching(it::close) }
            attachments.clear()
            if (provider is AutoCloseable) runCatching(provider::close)
        }
        fun attach(registration: AutoCloseable) {
            attachments += registration
        }

        private fun amountOperation(
            capability: CurrencyCapability,
            amount: BigDecimal,
            allowZero: Boolean = false,
            operation: (BigDecimal) -> CompletionStage<CurrencyResult>,
        ): CompletionStage<CurrencyResult> {
            if (state != CurrencyRegistrationState.ACTIVE) return unavailable()
            if (!supports(capability)) return unsupported(capability)
            if (!valid(amount, allowZero)) return invalidAmount()
            return safeResult { operation(descriptor.normalize(amount)) }
        }
        private fun resultOperation(
            capability: CurrencyCapability,
            operation: () -> CompletionStage<CurrencyResult>,
        ): CompletionStage<CurrencyResult> {
            if (state != CurrencyRegistrationState.ACTIVE) return unavailable()
            if (!supports(capability)) return unsupported(capability)
            return safeResult(operation)
        }
        private fun valid(amount: BigDecimal, allowZero: Boolean = false): Boolean =
            descriptor.accepts(amount) && if (allowZero) amount.signum() >= 0 else amount.signum() > 0
        private fun unavailable() = CompletableFuture.completedFuture(CurrencyResult.unavailable("Currency $key is not active"))
        private fun unsupported(capability: CurrencyCapability) = CompletableFuture.completedFuture(
            CurrencyResult.unsupported("Currency $key does not support $capability"),
        )
        private fun invalidAmount() = CompletableFuture.completedFuture(
            CurrencyResult.rejected(
                CurrencyRejectReason.INVALID_AMOUNT,
                "Amount must be positive and use at most ${descriptor.fractionDigits} fraction digits",
            ),
        )
        private fun <T> availableValue(operation: () -> CompletionStage<T>): CompletionStage<T> {
            if (state != CurrencyRegistrationState.ACTIVE) return failed("Currency $key is not active")
            return try {
                operation()
            } catch (error: Throwable) {
                failed("Currency $key failed", error)
            }
        }
        private fun safeResult(operation: () -> CompletionStage<CurrencyResult>): CompletionStage<CurrencyResult> = try {
            operation().exceptionally(CurrencyResult::failed)
        } catch (error: Throwable) {
            CompletableFuture.completedFuture(CurrencyResult.failed(error))
        }
    }

    private fun parseReference(consumer: PluginId, reference: String): CurrencyKey {
        val separator = reference.indexOf(':')
        return if (separator < 0) {
            CurrencyKey(consumer, normalizeCurrencyName(reference))
        } else {
            CurrencyKey(
                PluginId.of(reference.substring(0, separator)),
                normalizeCurrencyName(reference.substring(separator + 1)),
            )
        }
    }

    companion object {
        private fun <T> failed(message: String, cause: Throwable? = null): CompletionStage<T> = CompletableFuture<T>().also {
            it.completeExceptionally(IllegalStateException(message, cause))
        }
    }
}
