package org.pnfolder.pnlibrary.api.currency

import java.util.function.Consumer

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
