package org.pnfolder.pnlibrary.api.currency;

import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;

/** Synchronous operation that atomically transfers one amount between accounts. */
@FunctionalInterface
public interface CurrencyTransferOperation {
    /** Applies a transfer and returns its structured result. */
    @NotNull
    CurrencyResult apply(@NotNull CurrencyAccount from, @NotNull CurrencyAccount to, @NotNull BigDecimal amount);
}
