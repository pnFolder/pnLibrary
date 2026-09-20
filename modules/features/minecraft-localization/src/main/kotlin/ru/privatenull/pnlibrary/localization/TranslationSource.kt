package ru.privatenull.pnlibrary.localization

/** Where a parsed translation table was obtained. */
enum class TranslationSource { MEMORY, DISK, STALE_DISK, NETWORK }

/** Immutable provenance for a loaded locale. */
data class TranslationMetadata(
    val source: TranslationSource,
    val sha1: String?,
    val stale: Boolean,
)
