package ru.privatenull.pnlibrary.localization

/** One deterministic reverse-lookup result. [value] is null for keys unknown to the local API. */
data class TranslationMatch<T>(
    /** Canonical Minecraft translation key. */
    val key: String,
    /** Localized text matched by the query. */
    val translation: String,
    /** Local API object represented by [key], when known. */
    val value: T?,
)

/** Deterministic exact and fuzzy reverse lookup over one translation table. */
interface TranslationIndex<T> {
    /** Returns every entry whose localized text exactly equals [text]. */
    fun findExact(text: String): List<TranslationMatch<T>>
    /** Returns deterministic entries whose localized text matches [text]. */
    fun search(text: String): List<TranslationMatch<T>>
}
