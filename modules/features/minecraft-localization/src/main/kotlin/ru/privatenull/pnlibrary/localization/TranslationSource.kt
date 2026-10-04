package ru.privatenull.pnlibrary.localization

/** Where a parsed translation table was obtained. */
enum class TranslationSource { MEMORY, DISK, STALE_DISK, NETWORK }

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
