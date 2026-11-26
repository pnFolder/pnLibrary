package org.pnfolder.pnlibrary.currency;

import org.pnfolder.pnlibrary.currency.storage.BuiltInCurrencyStorage;
import java.util.function.BiConsumer;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.currency.CurrencyProviderRegistry;
import org.pnfolder.pnlibrary.api.currency.CurrencyService;
import org.pnfolder.pnlibrary.api.currency.CurrencyStorageFactory;
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderService;
import org.pnfolder.pnlibrary.api.plugin.PluginId;

/** Currency feature entry point: owns the registry hub and storage factory and hands plugins their scoped service. */
public final class CurrencyFeature implements CurrencyRuntime {
    private final CurrencyHub hub;
    private final CurrencyStorageFactory storages = new BuiltInCurrencyStorage();

    /** Creates the feature; provider failures are not reported anywhere. */
    public CurrencyFeature() {
        this((message, error) -> { });
    }

    /** Creates the feature; {@code report} receives every provider failure that would otherwise be lost. */
    public CurrencyFeature(@NotNull BiConsumer<String, Throwable> report) {
        this.hub = new CurrencyHub(report);
    }

    @Override
    @NotNull
    public CurrencyProviderRegistry getProviders() {
        return hub;
    }

    @Override
    @NotNull
    public CurrencyStorageFactory getStorages() {
        return storages;
    }

    @Override
    @NotNull
    public CurrencyService scope(@NotNull PluginId owner, @NotNull PlaceholderService placeholders) {
        return hub.scope(owner, placeholders);
    }

    @Override
    public void close() {
        hub.close();
    }
}
