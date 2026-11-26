package org.pnfolder.pnlibrary.api.currency;

/** Determines whether destructive administrative commands require a second approval step. */
public enum CurrencyConfirmationMode {
    /** Executes destructive operations immediately after permission checks. */
    NONE,

    /** Requires a time-limited approval through the server console. */
    CONSOLE
}
