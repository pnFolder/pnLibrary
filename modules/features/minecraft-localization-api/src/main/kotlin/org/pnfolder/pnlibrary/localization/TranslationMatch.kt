package org.pnfolder.pnlibrary.localization

import org.pnfolder.pnlibrary.localization.internal.TranslationIndexImpl

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

    companion object {
        /** Builds a reverse-search index from pre-resolved [matches]; used by platform modules to expose their own value types. */
        fun <T> of(matches: List<TranslationMatch<T>>): TranslationIndex<T> = TranslationIndexImpl(matches)
    }
}
