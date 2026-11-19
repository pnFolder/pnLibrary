package org.pnfolder.pnlibrary.api.observability.observation


/** Describes the operational importance and retention priority of an observation. */
enum class ObservationLevel {
    /** Detailed execution information intended for short-lived troubleshooting. */
    TRACE,

    /** Normal operational information. */
    INFO,

    /** A noteworthy state change that is not a failure. */
    NOTICE,

    /** A recoverable problem or degraded state. */
    WARNING,

    /** A failed operation that requires investigation. */
    ERROR,

    /** A severe incident retained independently of the normal history window. */
    CRITICAL,
}
