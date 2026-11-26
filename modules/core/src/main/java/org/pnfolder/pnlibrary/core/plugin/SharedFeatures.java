package org.pnfolder.pnlibrary.core.plugin;

import org.pnfolder.pnlibrary.api.expansions.ExpansionManager;
import org.pnfolder.pnlibrary.core.placeholders.PlaceholderEngine;
import org.pnfolder.pnlibrary.currency.CurrencyRuntime;

/** Library-wide features shared by all modules: placeholders, currency and expansions. */
public final class SharedFeatures {
    private final PlaceholderEngine placeholders;
    private final CurrencyRuntime currency;
    private final ExpansionManager expansions;
    private final ExpansionManager libraryExpansions;

    public SharedFeatures(PlaceholderEngine placeholders, CurrencyRuntime currency, ExpansionManager expansions,
                          ExpansionManager libraryExpansions) {
        this.placeholders = placeholders;
        this.currency = currency;
        this.expansions = expansions;
        this.libraryExpansions = libraryExpansions;
    }

    public PlaceholderEngine getPlaceholders() {
        return placeholders;
    }

    public CurrencyRuntime getCurrency() {
        return currency;
    }

    public ExpansionManager getExpansions() {
        return expansions;
    }

    public ExpansionManager getLibraryExpansions() {
        return libraryExpansions;
    }
}
