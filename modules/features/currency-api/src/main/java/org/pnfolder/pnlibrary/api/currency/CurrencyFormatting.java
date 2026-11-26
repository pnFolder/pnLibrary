package org.pnfolder.pnlibrary.api.currency;

import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;

/** Optional provider-specific presentation of normalized currency amounts. */
public interface CurrencyFormatting {
    /** Formats {@code amount} for display without changing any balance. */
    @NotNull
    String format(@NotNull BigDecimal amount);
}
