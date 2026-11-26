package org.pnfolder.pnlibrary.localization;

/** Where a parsed translation table was obtained. */
public enum TranslationSource {
    /** Served from the in-memory cache. */
    MEMORY,

    /** Read from the verified disk cache. */
    DISK,

    /** Read from disk after a refresh failed; the data may be outdated. */
    STALE_DISK,

    /** Downloaded from Mojang servers in this request. */
    NETWORK
}
