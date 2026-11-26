package org.pnfolder.pnlibrary.api.currency;

/** Persisted outcome of one transaction request. */
public enum CurrencyTransactionStatus {
    /** Balance mutation and ledger append completed atomically. */
    COMMITTED,

    /** Business validation refused the mutation, but the refusal was recorded. */
    REJECTED,

    /** A failed request was recorded without committing its intended mutation. */
    FAILED
}
