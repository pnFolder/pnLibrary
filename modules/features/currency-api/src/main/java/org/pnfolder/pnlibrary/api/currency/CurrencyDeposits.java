package org.pnfolder.pnlibrary.api.currency;

import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.concurrent.CompletionStage;

/** Optional capability for increasing an account balance. */
public interface CurrencyDeposits {
    /** Attempts to add positive {@code amount} to {@code account}. */
    @NotNull
    CompletionStage<CurrencyResult> deposit(@NotNull CurrencyAccount account, @NotNull BigDecimal amount);
}
