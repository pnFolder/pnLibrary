package org.pnfolder.pnlibrary.api.currency

import java.math.BigDecimal
import java.util.concurrent.CompletionStage

/**
 * Asynchronous persistent balance and ledger boundary.
 *
 * Implementations must commit each balance mutation and its ledger entry atomically.
 * Completion-stage failures represent infrastructure or malformed-storage errors;
 * business rejections are returned as persisted transaction records.
 */
interface CurrencyStorage : AutoCloseable {
    /** Returns the current balance, using zero for an account with no stored row. */
    fun balance(currency: CurrencyKey, account: CurrencyAccount): CompletionStage<BigDecimal>
    /** Atomically applies [request] and appends or returns its ledger record. */
    fun transact(currency: CurrencyKey, descriptor: CurrencyDescriptor, request: CurrencyTransactionRequest): CompletionStage<CurrencyTransaction>
    /** Returns a filtered reverse-chronological history page. */
    fun history(currency: CurrencyKey, query: CurrencyHistoryQuery): CompletionStage<CurrencyHistoryPage>
    /** Produces a consistent export of one currency. */
    fun export(currency: CurrencyKey): CompletionStage<CurrencyStorageSnapshot>
    /** Atomically imports balances and ledger rows according to [mode]. */
    fun importSnapshot(snapshot: CurrencyStorageSnapshot, mode: CurrencyImportMode): CompletionStage<CurrencyImportResult>
}
