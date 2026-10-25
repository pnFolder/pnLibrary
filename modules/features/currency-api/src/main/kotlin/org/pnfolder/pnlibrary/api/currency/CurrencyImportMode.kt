package org.pnfolder.pnlibrary.api.currency

/**
 * Conflict policy used when importing a [CurrencyStorageSnapshot].
 *
 * Use [REPLACE] for recovery into a disposable target. When consolidating two
 * live stores, select a merge mode according to which store owns authoritative
 * balances:
 *
 * ```kotlin
 * target.importSnapshot(snapshot, CurrencyImportMode.MERGE_KEEP_TARGET)
 * ```
 */
enum class CurrencyImportMode {
    /** Deletes all target balances and transactions, then restores the snapshot. */
    REPLACE,
    /** Keeps existing balances and imports only accounts and transactions absent from the target. */
    MERGE_KEEP_TARGET,
    /** Makes snapshot balances authoritative while still merging only missing transactions. */
    MERGE_OVERWRITE,
}
