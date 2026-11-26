package org.pnfolder.pnlibrary.currency;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.currency.CurrencyPlaceholderOptions;

/** Mutable placeholder switches collected from the plugin's configuration callback. */
final class ManagedPlaceholderOptions implements CurrencyPlaceholderOptions {
    private boolean enabled = true;
    private boolean placeholderApi;
    private String namespace;

    ManagedPlaceholderOptions(@NotNull String defaultNamespace) {
        this.namespace = defaultNamespace;
    }

    /** Whether placeholders are registered at all. */
    boolean isEnabled() {
        return enabled;
    }

    /** Whether the placeholders are also published to PlaceholderAPI. */
    boolean isPlaceholderApi() {
        return placeholderApi;
    }

    /** PlaceholderAPI identifier prefix. */
    @NotNull
    String getNamespace() {
        return namespace;
    }

    @Override
    @NotNull
    public CurrencyPlaceholderOptions enabled(boolean value) {
        enabled = value;
        return this;
    }

    @Override
    @NotNull
    public CurrencyPlaceholderOptions placeholderApi(boolean enabled) {
        placeholderApi = enabled;
        return this;
    }

    @Override
    @NotNull
    public CurrencyPlaceholderOptions placeholderApi(@NotNull String namespace) {
        if (CurrencyNames.isBlank(namespace)) {
            throw new IllegalArgumentException("PlaceholderAPI namespace must not be blank");
        }
        placeholderApi = true;
        this.namespace = namespace;
        return this;
    }
}
