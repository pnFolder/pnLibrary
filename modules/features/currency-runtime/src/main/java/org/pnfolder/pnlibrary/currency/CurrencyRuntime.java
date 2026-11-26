package org.pnfolder.pnlibrary.currency;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.currency.CurrencyProviderRegistry;
import org.pnfolder.pnlibrary.api.currency.CurrencyService;
import org.pnfolder.pnlibrary.api.currency.CurrencyStorageFactory;
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderService;
import org.pnfolder.pnlibrary.api.plugin.PluginId;

/** Runtime integration contract for shared providers and module-owned currency views. */
public interface CurrencyRuntime extends AutoCloseable {
    /** Shared provider registry presented by the runtime facade. */
    @NotNull
    CurrencyProviderRegistry getProviders();

    /** Storage factory registered in each module's services. */
    @NotNull
    CurrencyStorageFactory getStorages();

    /** Opens a module view that uses the module's {@code placeholders} and owns its registrations. */
    @NotNull
    CurrencyService scope(@NotNull PluginId owner, @NotNull PlaceholderService placeholders);

    @Override
    void close();
}
