package org.pnfolder.pnlibrary.api.currency;

/** Lifecycle state of a currency registration. */
public enum CurrencyRegistrationState {
    /** Visible and available for operations. */
    ACTIVE,

    /** Temporarily unavailable but eligible for {@link CurrencyRegistration#enable()}. */
    DISABLED,

    /** Permanently removed and no longer eligible for re-enabling. */
    CLOSED
}
