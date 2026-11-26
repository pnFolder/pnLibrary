package org.pnfolder.pnlibrary.api.currency;

import org.jetbrains.annotations.NotNull;

/** Generated placeholder publication options for a managed currency. */
public interface CurrencyPlaceholderOptions {
    /** Enables or disables generation of library placeholders. */
    @NotNull
    CurrencyPlaceholderOptions enabled(boolean value);

    /** Enables or disables publication through the PlaceholderAPI adapter. */
    @NotNull
    CurrencyPlaceholderOptions placeholderApi(boolean enabled);

    /** Enables PlaceholderAPI publication with explicit {@code namespace}. */
    @NotNull
    CurrencyPlaceholderOptions placeholderApi(@NotNull String namespace);
}
