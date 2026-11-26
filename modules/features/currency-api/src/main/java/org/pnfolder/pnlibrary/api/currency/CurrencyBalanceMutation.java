package org.pnfolder.pnlibrary.api.currency;

import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.concurrent.CompletionStage;

/** Optional capability for replacing an account balance. */
public interface CurrencyBalanceMutation {
    /** Attempts to replace the balance of {@code account} with {@code amount}. */
    @NotNull
    CompletionStage<CurrencyResult> setBalance(@NotNull CurrencyAccount account, @NotNull BigDecimal amount);
}
