package org.pnfolder.pnlibrary.api.currency;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.plugin.PluginId;

import java.util.List;

/** Restricted registry for platform integrations; consumer plugins use their owned {@link CurrencyService}. */
public interface CurrencyProviderRegistry {
    /** Registers a provider shared with every pnLibrary plugin. */
    @NotNull
    CurrencyRegistration register(@NotNull PluginId owner, @NotNull String name, @NotNull CurrencyProvider provider);

    /** Resolves an active canonical {@code owner:name} reference without consumer filtering. */
    @Nullable
    Currency get(@NotNull String reference);

    /** Resolves {@code reference} or fails when no active provider is registered. */
    @NotNull
    default Currency require(@NotNull String reference) {
        Currency currency = get(reference);
        if (currency == null) {
            throw new IllegalStateException("Currency " + reference + " is unavailable");
        }
        return currency;
    }

    /** Returns every active provider registration in canonical-key order. */
    @NotNull
    List<Currency> all();
}
