package org.pnfolder.pnlibrary.api.currency;

import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.concurrent.CompletionStage;

/**
 * Asynchronous persistent balance and ledger boundary.
 *
 * <p>Implementations must commit each balance mutation and its ledger entry atomically. Completion-stage failures
 * represent infrastructure or malformed-storage errors; business rejections are returned as persisted transaction
 * records.
 */
public interface CurrencyStorage extends AutoCloseable {
    /** Returns the current balance, using zero for an account with no stored row. */
    @NotNull
    CompletionStage<BigDecimal> balance(@NotNull CurrencyKey currency, @NotNull CurrencyAccount account);

    /** Atomically applies {@code request} and appends or returns its ledger record. */
    @NotNull
    CompletionStage<CurrencyTransaction> transact(
            @NotNull CurrencyKey currency, @NotNull CurrencyDescriptor descriptor,
            @NotNull CurrencyTransactionRequest request);

    /** Returns a filtered reverse-chronological history page. */
    @NotNull
    CompletionStage<CurrencyHistoryPage> history(@NotNull CurrencyKey currency, @NotNull CurrencyHistoryQuery query);

    /** Produces a consistent export of one currency. */
    @NotNull
    CompletionStage<CurrencyStorageSnapshot> export(@NotNull CurrencyKey currency);

    /** Atomically imports balances and ledger rows according to {@code mode}. */
    @NotNull
    CompletionStage<CurrencyImportResult> importSnapshot(
            @NotNull CurrencyStorageSnapshot snapshot, @NotNull CurrencyImportMode mode);
}
