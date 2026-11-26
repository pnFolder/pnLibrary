package org.pnfolder.pnlibrary.api.currency;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletionStage;

/** Optional capability for restoring an account to the provider-defined default balance. */
public interface CurrencyReset {
    /** Resets {@code account} and reports the previous and resulting balances when available. */
    @NotNull
    CompletionStage<CurrencyResult> reset(@NotNull CurrencyAccount account);
}
