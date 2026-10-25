package org.pnfolder.pnlibrary.api.currency

/**
 * Row-level summary of a completed snapshot import.
 *
 * @property currency imported currency identity
 * @property mode conflict policy applied to existing target data
 * @property importedAccounts balance rows inserted or overwritten
 * @property skippedAccounts existing balance rows retained
 * @property importedTransactions ledger rows inserted after duplicate checks
 * @property skippedTransactions ledger rows already identified by transaction ID or idempotency key
 */
data class CurrencyImportResult(
    /** Imported currency. */
    val currency: CurrencyKey,
    /** Conflict policy used for the import. */
    val mode: CurrencyImportMode,
    /** Balance rows inserted or overwritten. */
    val importedAccounts: Int,
    /** Existing balance rows retained. */
    val skippedAccounts: Int,
    /** Ledger rows inserted after duplicate checks. */
    val importedTransactions: Int,
    /** Rows skipped because transaction ID or idempotency key already existed. */
    val skippedTransactions: Int,
)
