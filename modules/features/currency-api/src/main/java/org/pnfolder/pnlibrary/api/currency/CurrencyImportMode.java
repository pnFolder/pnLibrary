package org.pnfolder.pnlibrary.api.currency;

/**
 * Conflict policy used when importing a {@link CurrencyStorageSnapshot}.
 *
 * <p>Use {@link #REPLACE} for recovery into a disposable target. When consolidating two live stores, select a merge
 * mode according to which store owns authoritative balances:
 *
 * <pre>{@code
 * target.importSnapshot(snapshot, CurrencyImportMode.MERGE_KEEP_TARGET);
 * }</pre>
 */
public enum CurrencyImportMode {
    /** Deletes all target balances and transactions, then restores the snapshot. */
    REPLACE,

    /** Keeps existing balances and imports only accounts and transactions absent from the target. */
    MERGE_KEEP_TARGET,

    /** Makes snapshot balances authoritative while still merging only missing transactions. */
    MERGE_OVERWRITE
}
