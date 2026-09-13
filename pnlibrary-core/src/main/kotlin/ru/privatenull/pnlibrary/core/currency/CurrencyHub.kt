package ru.privatenull.pnlibrary.core.currency

import ru.privatenull.pnlibrary.api.currency.*
import ru.privatenull.pnlibrary.api.plugin.PluginId
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.BiFunction
import java.util.function.Consumer
import java.util.function.Function

internal class CurrencyHub : CurrencyProviderRegistry, AutoCloseable {
    private val entries = ConcurrentHashMap<CurrencyKey, Entry>()
    private val aliases = ConcurrentHashMap<Pair<PluginId, String>, CurrencyKey>()
    private val closed = AtomicBoolean(false)

    fun scope(owner: PluginId): CurrencyService = Scope(owner)

    override fun register(owner: PluginId, name: String, provider: CurrencyProvider, access: CurrencyAccess): CurrencyRegistration =
        install(owner, name, provider, access, emptySet())

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        entries.values.toList().forEach(Entry::close)
        entries.clear()
        aliases.clear()
    }

    private fun install(owner: PluginId, name: String, provider: CurrencyProvider, access: CurrencyAccess, requestedAliases: Set<String>): CurrencyRegistration {
        check(!closed.get()) { "Currency registry is closed" }
        val key = CurrencyKey(owner, normalizeName(name))
        val normalizedAliases = requestedAliases.map(::normalizeName).toSet()
        val entry = Entry(key, provider, access, normalizedAliases)
        synchronized(entries) {
            require(!entries.containsKey(key)) { "Currency $key is already registered" }
            normalizedAliases.forEach { alias ->
                require(!aliases.containsKey(owner to alias) && !entries.containsKey(CurrencyKey(owner, alias))) { "Currency alias $owner:$alias is already registered" }
            }
            entries[key] = entry
            normalizedAliases.forEach { aliases[owner to it] = key }
        }
        return entry
    }

    private inner class Scope(private val owner: PluginId) : CurrencyService {
        private val owned = ConcurrentHashMap.newKeySet<CurrencyKey>()
        private val scopeClosed = AtomicBoolean(false)

        override fun register(name: String, configure: Consumer<CurrencyDefinitionBuilder>): CurrencyRegistration {
            checkOpen()
            val builder = DefinitionBuilder(name)
            configure.accept(builder)
            return own(install(owner, name, builder.provider(), builder.access, builder.aliases))
        }

        override fun register(name: String, provider: CurrencyProvider, configure: Consumer<CurrencyRegistrationOptions>): CurrencyRegistration {
            checkOpen()
            val options = Options()
            configure.accept(options)
            return own(install(owner, name, provider, options.access, options.aliases))
        }

        override fun get(reference: String): Currency? {
            val requested = parseReference(owner, reference)
            val key = aliases[requested.owner to requested.name] ?: requested
            return entries[key]?.takeIf { it.state == CurrencyRegistrationState.ACTIVE && it.access.allows(it.key.owner, owner) }
        }

        override fun all(): List<Currency> = entries.values
            .filter { it.state == CurrencyRegistrationState.ACTIVE && it.access.allows(it.key.owner, owner) }
            .sortedBy { it.key.toString() }

        override fun unregister(name: String) {
            val requested = CurrencyKey(owner, normalizeName(name))
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
        private val enabled = AtomicBoolean(true)
        private val entryClosed = AtomicBoolean(false)
        override val descriptor get() = provider.descriptor
        override val state get() = when {
            entryClosed.get() -> CurrencyRegistrationState.CLOSED
            enabled.get() -> CurrencyRegistrationState.ACTIVE
            else -> CurrencyRegistrationState.DISABLED
        }
        override val capabilities = if (provider is CapabilitySource) provider.declaredCapabilities else buildSet {
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
        override fun enable() { check(!entryClosed.get()) { "Currency $key is closed" }; enabled.set(true) }
        override fun disable() { enabled.set(false) }
        override fun close() {
            if (!entryClosed.compareAndSet(false, true)) return
            enabled.set(false)
            synchronized(entries) {
                entries.remove(key, this)
                registeredAliases.forEach { aliases.remove(key.owner to it, key) }
            }
            if (provider is AutoCloseable) runCatching(provider::close)
        }

        private fun amountOperation(capability: CurrencyCapability, amount: BigDecimal, allowZero: Boolean = false, operation: (BigDecimal) -> CompletionStage<CurrencyResult>): CompletionStage<CurrencyResult> {
            if (state != CurrencyRegistrationState.ACTIVE) return unavailable()
            if (!supports(capability)) return unsupported(capability)
            if (!valid(amount, allowZero)) return invalidAmount()
            return safeResult { operation(descriptor.normalize(amount)) }
        }
        private fun resultOperation(capability: CurrencyCapability, operation: () -> CompletionStage<CurrencyResult>): CompletionStage<CurrencyResult> {
            if (state != CurrencyRegistrationState.ACTIVE) return unavailable()
            if (!supports(capability)) return unsupported(capability)
            return safeResult(operation)
        }
        private fun valid(amount: BigDecimal, allowZero: Boolean = false) = descriptor.accepts(amount) && if (allowZero) amount.signum() >= 0 else amount.signum() > 0
        private fun unavailable() = CompletableFuture.completedFuture(CurrencyResult.unavailable("Currency $key is not active"))
        private fun unsupported(capability: CurrencyCapability) = CompletableFuture.completedFuture(CurrencyResult.unsupported("Currency $key does not support $capability"))
        private fun invalidAmount() = CompletableFuture.completedFuture(CurrencyResult.rejected(CurrencyRejectReason.INVALID_AMOUNT, "Amount must be positive and use at most ${descriptor.fractionDigits} fraction digits"))
        private fun <T> availableValue(operation: () -> CompletionStage<T>): CompletionStage<T> {
            if (state != CurrencyRegistrationState.ACTIVE) return failed("Currency $key is not active")
            return try { operation() } catch (error: Throwable) { failed("Currency $key failed", error) }
        }
        private fun safeResult(operation: () -> CompletionStage<CurrencyResult>): CompletionStage<CurrencyResult> = try {
            operation().exceptionally(CurrencyResult::failed)
        } catch (error: Throwable) {
            CompletableFuture.completedFuture(CurrencyResult.failed(error))
        }
    }

    private open inner class Options : CurrencyRegistrationOptions {
        var access: CurrencyAccess = CurrencyAccess.ownerOnly()
        val aliases = linkedSetOf<String>()
        override fun access(access: CurrencyAccess) = apply { this.access = access }
        override fun access(configure: Consumer<CurrencyAccess.Builder>) = apply { access = CurrencyAccess.builder().also(configure::accept).build() }
        override fun aliases(vararg aliases: String) = apply { this.aliases += aliases }
    }

    private inner class DefinitionBuilder(private val name: String) : Options(), CurrencyDefinitionBuilder {
        private val descriptor = CurrencyDescriptor.Builder(name)
        private val operations = Operations()
        override fun descriptor(configure: Consumer<CurrencyDescriptor.Builder>) = apply { configure.accept(descriptor) }
        override fun operations(configure: Consumer<CurrencyOperations>) = apply { configure.accept(operations) }
        fun provider() = operations.provider(descriptor.build(), name)
    }

    private class Operations : CurrencyOperations {
        private var balance: ((CurrencyAccount) -> CompletionStage<BigDecimal>)? = null
        private var deposit: ((CurrencyAccount, BigDecimal) -> CompletionStage<CurrencyResult>)? = null
        private var withdraw: ((CurrencyAccount, BigDecimal) -> CompletionStage<CurrencyResult>)? = null
        private var setBalance: ((CurrencyAccount, BigDecimal) -> CompletionStage<CurrencyResult>)? = null
        private var reset: ((CurrencyAccount) -> CompletionStage<CurrencyResult>)? = null
        private var transfer: ((CurrencyAccount, CurrencyAccount, BigDecimal) -> CompletionStage<CurrencyResult>)? = null
        private var formatter: ((BigDecimal) -> String)? = null
        private val extensions = linkedMapOf<Class<*>, Any>()

        override fun balance(operation: Function<CurrencyAccount, BigDecimal>) = apply { balance = { completed { operation.apply(it) } } }
        override fun balanceAsync(operation: Function<CurrencyAccount, CompletionStage<BigDecimal>>) = apply { balance = operation::apply }
        override fun deposit(operation: BiFunction<CurrencyAccount, BigDecimal, CurrencyResult>) = apply { deposit = { account, amount -> completed { operation.apply(account, amount) } } }
        override fun depositAsync(operation: BiFunction<CurrencyAccount, BigDecimal, CompletionStage<CurrencyResult>>) = apply { deposit = operation::apply }
        override fun withdraw(operation: BiFunction<CurrencyAccount, BigDecimal, CurrencyResult>) = apply { withdraw = { account, amount -> completed { operation.apply(account, amount) } } }
        override fun withdrawAsync(operation: BiFunction<CurrencyAccount, BigDecimal, CompletionStage<CurrencyResult>>) = apply { withdraw = operation::apply }
        override fun setBalance(operation: BiFunction<CurrencyAccount, BigDecimal, CurrencyResult>) = apply { setBalance = { account, amount -> completed { operation.apply(account, amount) } } }
        override fun reset(operation: Function<CurrencyAccount, CurrencyResult>) = apply { reset = { completed { operation.apply(it) } } }
        override fun transfer(operation: CurrencyTransferOperation) = apply { transfer = { from, to, amount -> completed { operation.apply(from, to, amount) } } }
        override fun format(operation: Function<BigDecimal, String>) = apply { formatter = operation::apply }
        override fun extension(type: Class<*>, value: Any) = apply { require(type.isInstance(value)); extensions[type] = value }

        fun provider(descriptor: CurrencyDescriptor, name: String): CurrencyProvider {
            val balances = balance ?: error("Currency $name requires a balance operation")
            return ConfiguredProvider(descriptor, balances, deposit, withdraw, setBalance, reset, transfer, formatter, extensions.toMap())
        }
    }

    private interface CapabilitySource { val declaredCapabilities: Set<CurrencyCapability> }

    private class ConfiguredProvider(
        override val descriptor: CurrencyDescriptor,
        private val balances: (CurrencyAccount) -> CompletionStage<BigDecimal>,
        private val deposits: ((CurrencyAccount, BigDecimal) -> CompletionStage<CurrencyResult>)?,
        private val withdrawals: ((CurrencyAccount, BigDecimal) -> CompletionStage<CurrencyResult>)?,
        private val mutation: ((CurrencyAccount, BigDecimal) -> CompletionStage<CurrencyResult>)?,
        private val resets: ((CurrencyAccount) -> CompletionStage<CurrencyResult>)?,
        private val transfers: ((CurrencyAccount, CurrencyAccount, BigDecimal) -> CompletionStage<CurrencyResult>)?,
        private val formatting: ((BigDecimal) -> String)?,
        private val extensions: Map<Class<*>, Any>,
    ) : CurrencyProvider, CurrencyDeposits, CurrencyWithdrawals, CurrencyBalanceMutation, CurrencyReset, CurrencyTransfers, CurrencyFormatting, CapabilitySource {
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
        override fun transfer(from: CurrencyAccount, to: CurrencyAccount, amount: BigDecimal) = transfers?.invoke(from, to, amount) ?: unsupported()
        override fun format(amount: BigDecimal) = formatting?.invoke(amount) ?: descriptor.normalize(amount).toPlainString() + descriptor.symbol
        override fun <T : Any> extension(type: Class<T>): T? = extensions[type]?.let(type::cast)
        private fun unsupported() = CompletableFuture.completedFuture(CurrencyResult.unsupported("Operation is not configured"))
    }

    private fun parseReference(consumer: PluginId, reference: String): CurrencyKey {
        val separator = reference.indexOf(':')
        return if (separator < 0) CurrencyKey(consumer, normalizeName(reference)) else CurrencyKey(PluginId.of(reference.substring(0, separator)), normalizeName(reference.substring(separator + 1)))
    }
    private fun normalizeName(value: String) = value.trim().lowercase().also { require(it.matches(Regex("[a-z0-9_.-]+"))) { "Invalid currency name: $value" } }

    companion object {
        private fun <T> completed(operation: () -> T): CompletionStage<T> = try {
            CompletableFuture.completedFuture(operation())
        } catch (error: Throwable) {
            failed("Currency operation failed", error)
        }
        private fun <T> failed(message: String, cause: Throwable? = null): CompletionStage<T> = CompletableFuture<T>().also {
            it.completeExceptionally(IllegalStateException(message, cause))
        }
    }
}
