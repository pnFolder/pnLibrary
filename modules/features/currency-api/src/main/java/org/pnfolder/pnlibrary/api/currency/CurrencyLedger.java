package org.pnfolder.pnlibrary.api.currency;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletionStage;

/** Advanced managed-currency API available through {@code currency.extension(CurrencyLedger.class)}. */
public interface CurrencyLedger {
    /** Applies one managed-currency transaction request. */
    @NotNull
    CompletionStage<CurrencyTransaction> transact(@NotNull CurrencyTransactionRequest request);

    /** Queries the managed currency's persisted ledger. */
    @NotNull
    CompletionStage<CurrencyHistoryPage> history(@NotNull CurrencyHistoryQuery query);
}
