package org.pnfolder.pnlibrary.api.placeholders

/** Current lifecycle state of an external placeholder adapter or publication. */
enum class PlaceholderAdapterState {
    /** Adapter is installed and can accept publications. */
    AVAILABLE,
    /** Requested adapter is not currently installed. */
    UNAVAILABLE,
    /** Placeholder has been published successfully. */
    REGISTERED,
    /** Adapter discovery or publication failed. */
    FAILED,
    /** Registration was permanently closed. */
    CLOSED,
}
