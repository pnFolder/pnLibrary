package org.pnfolder.pnlibrary.api.currency

import org.pnfolder.pnlibrary.api.plugin.PluginId

/** Restricted registry for platform integrations; consumer plugins use their owned [CurrencyService]. */
interface CurrencyProviderRegistry {
    /** Registers a provider shared with every pnLibrary plugin. */
    fun register(owner: PluginId, name: String, provider: CurrencyProvider): CurrencyRegistration = register(owner, name, provider, CurrencyAccess.shared())
    /** Registers a provider with explicit cross-plugin [access]. */
    fun register(owner: PluginId, name: String, provider: CurrencyProvider, access: CurrencyAccess): CurrencyRegistration
    /** Resolves an active canonical `owner:name` reference without consumer filtering. */
    fun get(reference: String): Currency?
    /** Resolves [reference] or fails when no active provider is registered. */
    fun require(reference: String): Currency =
        get(reference) ?: error("Currency $reference is unavailable")
    /** Returns every active provider registration in canonical-key order. */
    fun all(): List<Currency>
}
