package org.pnfolder.pnlibrary.currency

import org.pnfolder.pnlibrary.api.currency.CurrencyProviderRegistry
import org.pnfolder.pnlibrary.api.currency.CurrencyService
import org.pnfolder.pnlibrary.api.currency.CurrencyStorageFactory
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderService
import org.pnfolder.pnlibrary.api.plugin.PluginId

/** Runtime integration contract for shared providers and module-owned currency views. */
interface CurrencyRuntime : AutoCloseable {
    /** Shared provider registry presented by the runtime facade. */
    val providers: CurrencyProviderRegistry

    /** Storage factory registered in each module's services. */
    val storages: CurrencyStorageFactory

    /** Opens a module view that uses the module's [placeholders] and owns its registrations. */
    fun scope(owner: PluginId, placeholders: PlaceholderService): CurrencyService
}
