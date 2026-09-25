package ru.privatenull.pnlibrary.currency

import ru.privatenull.pnlibrary.api.currency.CurrencyProviderRegistry
import ru.privatenull.pnlibrary.api.currency.CurrencyService
import ru.privatenull.pnlibrary.api.currency.CurrencyStorageFactory
import ru.privatenull.pnlibrary.api.placeholders.PlaceholderService
import ru.privatenull.pnlibrary.api.plugin.PluginId

/** Narrow integration boundary between the currency feature and pnLibrary core. */
class CurrencyFeature : AutoCloseable {
    private val hub = CurrencyHub()

    /** Global provider registry exposed by the pnLibrary facade. */
    val providers: CurrencyProviderRegistry get() = hub

    /** Factory for the built-in file and JDBC currency storages. */
    val storages: CurrencyStorageFactory = CurrencyStorageFactoryImpl()

    /** Creates an isolated currency view owned by one registered module. */
    fun scope(owner: PluginId, placeholders: PlaceholderService): CurrencyService = hub.scope(owner, placeholders)

    /** Closes registrations and providers owned by this feature. */
    override fun close() = hub.close()
}
