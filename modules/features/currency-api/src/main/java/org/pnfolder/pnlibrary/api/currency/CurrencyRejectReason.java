package org.pnfolder.pnlibrary.api.currency;

/** Stable business reason accompanying {@link CurrencyResultStatus#REJECTED}. */
public enum CurrencyRejectReason {
    /** Amount is negative, zero where forbidden, or violates decimal rules. */
    INVALID_AMOUNT,

    /** Source account cannot cover the requested debit. */
    INSUFFICIENT_FUNDS,

    /** Target account does not exist and cannot be created. */
    ACCOUNT_NOT_FOUND,

    /** Provider-defined account or transaction limit would be exceeded. */
    LIMIT_EXCEEDED,

    /** Provider rejected the request without a more specific standard reason. */
    PROVIDER_REJECTED
}
