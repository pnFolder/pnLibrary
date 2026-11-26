package org.pnfolder.pnlibrary.api.logging;

/** Semantic severity and presentation category for plugin log messages. */
public enum LogLevel {
    /** Detail for a support report; the console does not show it. */
    DEBUG,

    /** Normal operational information. */
    INFO,

    /** Positive completion or readiness information. */
    SUCCESS,

    /** Recoverable problem that may require administrator attention. */
    WARNING,

    /** Failed operation or unexpected exception. */
    ERROR,

    /** Failure that leaves the plugin or the server unable to do its job; always reaches the black box. */
    CRITICAL
}
