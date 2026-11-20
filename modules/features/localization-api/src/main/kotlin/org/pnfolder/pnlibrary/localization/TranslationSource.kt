package org.pnfolder.pnlibrary.localization

/** Where a parsed translation table was obtained. */
enum class TranslationSource {
    /** Served from the in-memory cache. */
    MEMORY,
    /** Read from the verified disk cache. */
    DISK,
    /** Read from disk after a refresh failed; the data may be outdated. */
    STALE_DISK,
    /** Downloaded from Mojang servers in this request. */
    NETWORK,
}

/**
 * Immutable provenance for a loaded locale.
 *
 * @property source cache/network source used for this table
 * @property sha1 Mojang-provided content digest, when available
 * @property stale whether an expired disk entry was used as an offline fallback
 */
data class TranslationMetadata(
    val source: TranslationSource,
    val sha1: String?,
    val stale: Boolean,
)
