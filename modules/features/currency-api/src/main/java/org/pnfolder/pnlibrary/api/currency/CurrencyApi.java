package org.pnfolder.pnlibrary.api.currency;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.plugin.ModuleContext;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;

/** Java-friendly entry point for the optional currency feature. */
public final class CurrencyApi {
    private CurrencyApi() {
    }

    /** Returns the currency service scoped to {@code context}. */
    @NotNull
    public static CurrencyService from(@NotNull ModuleContext context) {
        return context.getServices().require(CurrencyService.class);
    }

    /** Returns built-in currency storage factories available to {@code context}. */
    @NotNull
    public static CurrencyStorageFactory storages(@NotNull ModuleContext context) {
        return context.getServices().require(CurrencyStorageFactory.class);
    }

    /** Returns the runtime-wide registry used by platform currency adapters. */
    @NotNull
    public static CurrencyProviderRegistry providers(@NotNull PnLibrary library) {
        return library.getServices().require(CurrencyProviderRegistry.class);
    }
}
