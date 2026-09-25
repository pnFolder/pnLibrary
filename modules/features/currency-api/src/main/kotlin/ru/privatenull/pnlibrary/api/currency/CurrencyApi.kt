package ru.privatenull.pnlibrary.api.currency

import ru.privatenull.pnlibrary.api.plugin.ModuleContext
import ru.privatenull.pnlibrary.api.runtime.PnLibrary

/** Java-friendly entry point for the optional currency feature. */
object CurrencyApi {
    /** Returns the currency service scoped to [context]. */
    @JvmStatic fun from(context: ModuleContext): CurrencyService =
        context.services.require(CurrencyService::class.java)

    /** Returns built-in currency storage factories available to [context]. */
    @JvmStatic fun storages(context: ModuleContext): CurrencyStorageFactory =
        context.services.require(CurrencyStorageFactory::class.java)

    /** Returns the runtime-wide registry used by platform currency adapters. */
    @JvmStatic fun providers(library: PnLibrary): CurrencyProviderRegistry =
        library.services.require(CurrencyProviderRegistry::class.java)
}

/** Kotlin access to the optional module-scoped currency service. */
val ModuleContext.currencies: CurrencyService get() = CurrencyApi.from(this)

/** Kotlin access to built-in file and JDBC currency storages. */
val ModuleContext.currencyStorages: CurrencyStorageFactory get() = CurrencyApi.storages(this)

/** Kotlin access to runtime-wide platform currency providers. */
val PnLibrary.currencyProviders: CurrencyProviderRegistry get() = CurrencyApi.providers(this)
