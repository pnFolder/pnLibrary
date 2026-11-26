package org.pnfolder.pnlibrary.api.currency;

import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.concurrent.CompletionStage;

/** Optional capability for atomically moving value between two accounts. */
public interface CurrencyTransfers {
    /** Attempts one indivisible transfer of positive {@code amount} from {@code from} to {@code to}. */
    @NotNull
    CompletionStage<CurrencyResult> transfer(
            @NotNull CurrencyAccount from, @NotNull CurrencyAccount to, @NotNull BigDecimal amount);
}
