package ru.privatenull.pnlibrary.core.currency

import ru.privatenull.pnlibrary.api.currency.*
import ru.privatenull.pnlibrary.api.plugin.PluginId
import java.math.BigDecimal
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.BiFunction
import java.util.function.Consumer
import java.util.function.Function
import java.util.UUID

internal class CurrencyHub : CurrencyProviderRegistry, AutoCloseable {
    private val registrations = ConcurrentHashMap<CurrencyKey, Registration>()
    private val closed = AtomicBoolean(false)

    fun scope(owner: PluginId): CurrencyService = Scope(owner)

    override fun register(owner: PluginId, name: String, provider: CurrencyProvider, access: CurrencyAccess): CurrencyRegistration =
        install(owner, name, provider, access)

    private fun install(owner: PluginId, name: String, provider: CurrencyProvider, access: CurrencyAccess): CurrencyRegistration {
        check(!closed.get()) { "Currency registry is closed" }
        val key = CurrencyKey(owner, name.lowercase())
        val registration = Registration(key, provider, access)
        require(registrations.putIfAbsent(key, registration) == null) { "Currency $key is already registered" }
        return registration
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        registrations.values.toList().forEach(Registration::close)
        registrations.clear()
    }

    private inner class Scope(private val owner: PluginId) : CurrencyService {
        private val owned = ConcurrentHashMap.newKeySet<CurrencyKey>()
        private val scopeClosed = AtomicBoolean(false)

        override fun currency(name: String): CurrencyBuilder = Builder(owner, name) { provider, access ->
            register(name, provider, access)
        }

        override fun register(name: String, provider: CurrencyProvider, access: CurrencyAccess): CurrencyRegistration {
            check(!scopeClosed.get()) { "Currencies for $owner are closed" }
            val registration = install(owner, name, provider, access)
            owned += registration.key
            return registration
        }

        override fun get(reference: String): CurrencyRegistration? {
            val key = parseReference(owner, reference)
            return registrations[key]?.takeIf { it.isEnabled && it.access.allows(it.key.owner, owner) }
        }

        override fun all(): List<CurrencyRegistration> = registrations.values
            .filter { it.isEnabled && it.access.allows(it.key.owner, owner) }
            .sortedBy { it.key.toString() }

        override fun unregister(name: String) {
            registrations[CurrencyKey(owner, name.lowercase())]?.close()
        }

        override fun close() {
            if (!scopeClosed.compareAndSet(false, true)) return
            owned.toList().forEach { registrations[it]?.close() }
            owned.clear()
        }
    }

    private inner class Registration(
        override val key: CurrencyKey,
        override val provider: CurrencyProvider,
        val access: CurrencyAccess,
    ) : CurrencyRegistration {
        private val enabled = AtomicBoolean(true)
        private val registrationClosed = AtomicBoolean(false)
        override val isEnabled: Boolean get() = enabled.get() && !registrationClosed.get()
        override fun balance(playerId: UUID): CompletionStage<BigDecimal> =
            if (!isEnabled) failedCurrency("Currency $key is disabled")
            else safely { provider.balance(CurrencyAccount(playerId)) }
                .thenApply { provider.definition.normalize(it) }
        override fun has(playerId: UUID, amount: BigDecimal): CompletionStage<Boolean> {
            if (!valid(amount, allowZero = true)) return CompletableFuture.completedFuture(false)
            return balance(playerId).thenApply { it >= provider.definition.normalize(amount) }
        }
        override fun deposit(playerId: UUID, amount: BigDecimal) =
            mutate(CurrencyCapability.DEPOSIT, CurrencyAccount(playerId), amount, provider::deposit)
        override fun withdraw(playerId: UUID, amount: BigDecimal) =
            mutate(CurrencyCapability.WITHDRAW, CurrencyAccount(playerId), amount, provider::withdraw)
        override fun setBalance(playerId: UUID, amount: BigDecimal) =
            mutate(CurrencyCapability.SET_BALANCE, CurrencyAccount(playerId), amount, provider::setBalance, allowZero = true)
        override fun reset(playerId: UUID): CompletionStage<CurrencyResult> {
            if (!isEnabled) return CompletableFuture.completedFuture(CurrencyResult.unavailable("Currency $key is disabled"))
            if (!supports(CurrencyCapability.RESET)) return unsupported(CurrencyCapability.RESET)
            return safelyResult { provider.reset(CurrencyAccount(playerId)) }
        }
        override fun transfer(from: UUID, to: UUID, amount: BigDecimal): CompletionStage<CurrencyResult> {
            if (!isEnabled) return CompletableFuture.completedFuture(CurrencyResult.unavailable("Currency $key is disabled"))
            if (!supports(CurrencyCapability.TRANSFER)) return unsupported(CurrencyCapability.TRANSFER)
            if (!valid(amount)) return invalidAmount()
            return safelyResult { provider.transfer(CurrencyAccount(from), CurrencyAccount(to), definition.normalize(amount)) }
        }
        override fun enable() { check(!registrationClosed.get()) { "Currency $key is closed" }; enabled.set(true) }
        override fun disable() { enabled.set(false) }
        override fun close() {
            if (registrationClosed.compareAndSet(false, true)) {
                enabled.set(false)
                registrations.remove(key, this)
                if (provider is AutoCloseable) runCatching(provider::close)
            }
        }

        private fun mutate(
            capability: CurrencyCapability,
            account: CurrencyAccount,
            amount: BigDecimal,
            operation: (CurrencyAccount, BigDecimal) -> CompletionStage<CurrencyResult>,
            allowZero: Boolean = false,
        ): CompletionStage<CurrencyResult> {
            if (!isEnabled) return CompletableFuture.completedFuture(CurrencyResult.unavailable("Currency $key is disabled"))
            if (!supports(capability)) return unsupported(capability)
            if (!valid(amount, allowZero)) return invalidAmount()
            return safelyResult { operation(account, definition.normalize(amount)) }
        }

        private fun valid(amount: BigDecimal, allowZero: Boolean = false): Boolean =
            definition.accepts(amount) && if (allowZero) amount.signum() >= 0 else amount.signum() > 0
    }

    private inner class Builder(
        private val owner: PluginId,
        name: String,
        private val install: (CurrencyProvider, CurrencyAccess) -> CurrencyRegistration,
    ) : CurrencyBuilder {
        private val name = name.lowercase()
        private var displayName = name
        private var symbol = ""
        private var fractionDigits = 2
        private var access = CurrencyAccess.ownerOnly()
        private var balance: ((CurrencyAccount) -> CompletionStage<BigDecimal>)? = null
        private var deposit: ((CurrencyAccount, BigDecimal) -> CompletionStage<CurrencyResult>)? = null
        private var withdraw: ((CurrencyAccount, BigDecimal) -> CompletionStage<CurrencyResult>)? = null
        private var setBalance: ((CurrencyAccount, BigDecimal) -> CompletionStage<CurrencyResult>)? = null
        private var reset: ((CurrencyAccount) -> CompletionStage<CurrencyResult>)? = null
        private var transfer: ((CurrencyAccount, CurrencyAccount, BigDecimal) -> CompletionStage<CurrencyResult>)? = null
        private var formatter: ((BigDecimal) -> String)? = null
        private val extensions = linkedMapOf<Class<*>, Any>()

        override fun displayName(value: String) = apply { displayName = value }
        override fun symbol(value: String) = apply { symbol = value }
        override fun fractionDigits(value: Int) = apply { fractionDigits = value }
        override fun access(value: CurrencyAccess) = apply { access = value }
        override fun access(configure: Consumer<CurrencyAccess.Builder>) = apply {
            access = CurrencyAccess.builder().also(configure::accept).build()
        }
        override fun balance(operation: Function<CurrencyAccount, BigDecimal>) = apply {
            balance = { CompletableFuture.completedFuture(operation.apply(it)) }
        }
        override fun balanceAsync(operation: Function<CurrencyAccount, CompletionStage<BigDecimal>>) = apply { balance = operation::apply }
        override fun deposit(operation: BiFunction<CurrencyAccount, BigDecimal, CurrencyResult>) = apply {
            deposit = { account, amount -> CompletableFuture.completedFuture(operation.apply(account, amount)) }
        }
        override fun depositAsync(operation: BiFunction<CurrencyAccount, BigDecimal, CompletionStage<CurrencyResult>>) = apply { deposit = operation::apply }
        override fun withdraw(operation: BiFunction<CurrencyAccount, BigDecimal, CurrencyResult>) = apply {
            withdraw = { account, amount -> CompletableFuture.completedFuture(operation.apply(account, amount)) }
        }
        override fun withdrawAsync(operation: BiFunction<CurrencyAccount, BigDecimal, CompletionStage<CurrencyResult>>) = apply { withdraw = operation::apply }
        override fun setBalance(operation: BiFunction<CurrencyAccount, BigDecimal, CurrencyResult>) = apply {
            setBalance = { account, amount -> CompletableFuture.completedFuture(operation.apply(account, amount)) }
        }
        override fun reset(operation: Function<CurrencyAccount, CurrencyResult>) = apply {
            reset = { CompletableFuture.completedFuture(operation.apply(it)) }
        }
        override fun transfer(operation: TransferOperation) = apply {
            transfer = { from, to, amount -> CompletableFuture.completedFuture(operation.apply(from, to, amount)) }
        }
        override fun formatter(operation: Function<BigDecimal, String>) = apply { formatter = operation::apply }
        override fun extension(type: Class<*>, value: Any) = apply {
            require(type.isInstance(value)) { "${value.javaClass.name} does not implement ${type.name}" }
            extensions[type] = value
        }

        override fun register(): CurrencyRegistration {
            val definition = CurrencyDefinition(displayName, symbol, fractionDigits)
            val balanceOperation = balance ?: error("Currency $owner:$name has no balance operation")
            val capabilities = buildSet {
                add(CurrencyCapability.BALANCE)
                if (fractionDigits > 0) add(CurrencyCapability.FRACTIONAL_AMOUNTS)
                if (deposit != null) add(CurrencyCapability.DEPOSIT)
                if (withdraw != null) add(CurrencyCapability.WITHDRAW)
                if (setBalance != null) add(CurrencyCapability.SET_BALANCE)
                if (reset != null) add(CurrencyCapability.RESET)
                if (transfer != null) add(CurrencyCapability.TRANSFER)
                if (formatter != null) add(CurrencyCapability.FORMATTING)
            }
            val provider = object : CurrencyProvider {
                override val definition = definition
                override val capabilities = capabilities
                override fun balance(account: CurrencyAccount) = balanceOperation(account)
                override fun deposit(account: CurrencyAccount, amount: BigDecimal) = executeAmount(definition, CurrencyCapability.DEPOSIT, account, amount, deposit)
                override fun withdraw(account: CurrencyAccount, amount: BigDecimal) = executeAmount(definition, CurrencyCapability.WITHDRAW, account, amount, withdraw)
                override fun setBalance(account: CurrencyAccount, amount: BigDecimal) = executeAmount(definition, CurrencyCapability.SET_BALANCE, account, amount, setBalance)
                override fun reset(account: CurrencyAccount) = reset?.invoke(account) ?: unsupported(CurrencyCapability.RESET)
                override fun transfer(from: CurrencyAccount, to: CurrencyAccount, amount: BigDecimal): CompletionStage<CurrencyResult> {
                    if (!definition.accepts(amount) || amount.signum() <= 0) return invalidAmount()
                    return transfer?.invoke(from, to, definition.normalize(amount)) ?: unsupported(CurrencyCapability.TRANSFER)
                }
                override fun format(amount: BigDecimal) = formatter?.invoke(definition.normalize(amount)) ?: super.format(amount)
                override fun <T : Any> extension(type: Class<T>): T? = extensions[type]?.let(type::cast)
            }
            return install(provider, access)
        }
    }

    private fun executeAmount(
        definition: CurrencyDefinition,
        capability: CurrencyCapability,
        account: CurrencyAccount,
        amount: BigDecimal,
        operation: ((CurrencyAccount, BigDecimal) -> CompletionStage<CurrencyResult>)?,
    ): CompletionStage<CurrencyResult> {
        if (!definition.accepts(amount) || amount.signum() <= 0) return invalidAmount()
        return operation?.invoke(account, definition.normalize(amount)) ?: unsupported(capability)
    }

    private fun invalidAmount() = CompletableFuture.completedFuture(
        CurrencyResult.rejected(CurrencyRejectReason.INVALID_AMOUNT, "Amount must be positive and use the configured precision")
    )
    private fun unsupported(capability: CurrencyCapability) =
        CompletableFuture.completedFuture(CurrencyResult.unsupported("Currency does not support $capability"))

    private fun <T> safely(operation: () -> CompletionStage<T>): CompletionStage<T> = try {
        operation()
    } catch (error: Throwable) {
        failedCurrency(error.message ?: error.javaClass.simpleName, error)
    }

    private fun safelyResult(operation: () -> CompletionStage<CurrencyResult>): CompletionStage<CurrencyResult> = try {
        operation().exceptionally(CurrencyResult::failed)
    } catch (error: Throwable) {
        CompletableFuture.completedFuture(CurrencyResult.failed(error))
    }

    private fun <T> failedCurrency(message: String, cause: Throwable? = null): CompletionStage<T> =
        CompletableFuture<T>().also { it.completeExceptionally(IllegalStateException(message, cause)) }

    private fun parseReference(consumer: PluginId, reference: String): CurrencyKey {
        val separator = reference.indexOf(':')
        return if (separator < 0) CurrencyKey(consumer, reference.lowercase()) else CurrencyKey(
            PluginId.of(reference.substring(0, separator)), reference.substring(separator + 1).lowercase()
        )
    }
}
