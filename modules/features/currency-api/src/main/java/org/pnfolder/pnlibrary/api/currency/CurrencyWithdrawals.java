package org.pnfolder.pnlibrary.api.currency;

import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.concurrent.CompletionStage;

/** Optional capability for decreasing an account balance. */
public interface CurrencyWithdrawals {
    /** Attempts to subtract positive {@code amount} from {@code account}. */
    @NotNull
    CompletionStage<CurrencyResult> withdraw(@NotNull CurrencyAccount account, @NotNull BigDecimal amount);
}
