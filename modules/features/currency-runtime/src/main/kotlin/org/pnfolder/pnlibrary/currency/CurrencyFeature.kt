package org.pnfolder.pnlibrary.currency

import org.pnfolder.pnlibrary.api.currency.CurrencyProviderRegistry
import org.pnfolder.pnlibrary.api.currency.CurrencyService
import org.pnfolder.pnlibrary.api.currency.CurrencyStorageFactory
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderService
import org.pnfolder.pnlibrary.api.plugin.PluginId

/** Narrow integration boundary between the currency feature and pnLibrary core. */
class CurrencyFeature : CurrencyRuntime {
    private val hub = CurrencyHub()

    /** Global provider registry exposed by the pnLibrary facade. */
    override val providers: CurrencyProviderRegistry get() = hub

    /** Factory for the built-in file and JDBC currency storages. */
    override val storages: CurrencyStorageFactory = CurrencyStorageFactoryImpl()

    /** Creates an isolated currency view owned by one registered module. */
    override fun scope(owner: PluginId, placeholders: PlaceholderService): CurrencyService = hub.scope(owner, placeholders)

    /** Closes registrations and providers owned by this feature. */
    override fun close() = hub.close()
}
