package org.pnfolder.pnlibrary.api.currency;

/** Outcome category of a currency mutation. */
public enum CurrencyResultStatus {
    /** Mutation committed successfully. */
    SUCCESS,

    /** Valid operation was refused for a known business reason. */
    REJECTED,

    /** Provider does not implement the requested capability. */
    UNSUPPORTED,

    /** Capability exists but its backend is temporarily unavailable. */
    UNAVAILABLE,

    /** Unexpected implementation or infrastructure failure. */
    FAILED
}
