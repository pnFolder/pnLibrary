package org.pnfolder.pnlibrary.currency;

import java.math.BigDecimal;
import java.util.concurrent.CompletionStage;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.currency.CurrencyAccount;
import org.pnfolder.pnlibrary.api.currency.CurrencyResult;

/** A transfer operation whose result arrives later. */
interface AsyncTransfer {
    @NotNull
    CompletionStage<CurrencyResult> apply(@NotNull CurrencyAccount from, @NotNull CurrencyAccount to, @NotNull BigDecimal amount);
}
