package org.pnfolder.pnlibrary.api.currency;

/** Balance mutation represented by a ledger transaction. */
public enum CurrencyTransactionType {
    /** Adds {@link CurrencyTransaction#getAmount()} to the target account. */
    CREDIT,

    /** Subtracts the amount from the source when sufficient funds exist. */
    DEBIT,

    /** Atomically subtracts from the source and adds to the target. */
    TRANSFER,

    /** Replaces the target balance with the amount. */
    SET_BALANCE,

    /** Restores the target account balance to zero. */
    RESET
}
