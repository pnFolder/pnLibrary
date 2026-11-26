package org.pnfolder.pnlibrary.api.currency;

/** Operations that a {@link CurrencyProvider} may expose beyond mandatory balance lookup. */
public enum CurrencyCapability {
    /** Mandatory balance lookup. */
    BALANCE,

    /** {@link CurrencyDeposits} support. */
    DEPOSIT,

    /** {@link CurrencyWithdrawals} support. */
    WITHDRAW,

    /** {@link CurrencyBalanceMutation} support. */
    SET_BALANCE,

    /** {@link CurrencyReset} support. */
    RESET,

    /** {@link CurrencyTransfers} support. */
    TRANSFER,

    /** {@link CurrencyFormatting} support. */
    FORMATTING
}
