package org.pnfolder.pnlibrary.currency

import org.pnfolder.pnlibrary.api.currency.CurrencyProviderRegistry
import org.pnfolder.pnlibrary.api.currency.CurrencyService
import org.pnfolder.pnlibrary.api.currency.CurrencyStorageFactory
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderService
import org.pnfolder.pnlibrary.api.plugin.PluginId

/** Narrow integration boundary between the currency feature and pnLibrary core. */
class CurrencyFeature : CurrencyRuntime {
    private val hub = CurrencyHub()

    override val providers: CurrencyProviderRegistry get() = hub

    override val storages: CurrencyStorageFactory = BuiltInCurrencyStorage()

    override fun scope(owner: PluginId, placeholders: PlaceholderService): CurrencyService = hub.scope(owner, placeholders)

    override fun close() = hub.close()
}
