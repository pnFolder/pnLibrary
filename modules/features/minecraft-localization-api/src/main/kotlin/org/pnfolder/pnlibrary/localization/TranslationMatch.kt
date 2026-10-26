package org.pnfolder.pnlibrary.localization

import org.pnfolder.pnlibrary.localization.internal.TranslationIndexImpl
import org.pnfolder.pnlibrary.localization.layout.KeyboardLayout
import org.pnfolder.pnlibrary.localization.layout.KeyboardLayouts

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
    /** The layouts that [suggest] and [best] read queries against when a call passes none. */
    val layouts: KeyboardLayouts get() = KeyboardLayouts.builtIn

    /** Returns every entry whose localized text exactly equals [text]. */
    fun findExact(text: String): List<TranslationMatch<T>>
    /** Returns deterministic entries whose localized text matches [text]. */
    fun search(text: String): List<TranslationMatch<T>>

    /**
     * Returns entries whose localized text, or any single word of it, resembles [text] despite typos, best first and
     * at most [limit] of them. [minSimilarity] runs from 0.0 to 1.0, where 1.0 is an identical word. Similarity is the
     * better of an edit score (a missing, extra, wrong or swapped neighbouring letter is one error) and a score
     * over shared letter triples, which still ranks long words with scrambled letters.
     */
    fun fuzzy(text: String, minSimilarity: Double, limit: Int): List<TranslationMatch<T>>

    /** Same as the three-argument form with a similarity of 0.7 and at most 5 results. */
    fun fuzzy(text: String): List<TranslationMatch<T>> = fuzzy(text, minSimilarity = 0.7, limit = 5)

    /**
     * "Did you mean" candidates for [text], at most [limit]. Entries containing the text come first, in [search]
     * order, followed by fuzzy matches of at least 0.5 similarity, best first and then by key. The text is also read
     * as if typed on another of [layouts]; a match of the text as typed wins a tie. An empty [layouts] turns this off.
     */
    fun suggest(text: String, limit: Int, layouts: Collection<KeyboardLayout>): List<TranslationMatch<T>>

    /** Same as the three-argument form with this index's [layouts]. */
    fun suggest(text: String, limit: Int): List<TranslationMatch<T>> = suggest(text, limit, layouts.all())

    /** Same as the two-argument form with at most 5 results. */
    fun suggest(text: String): List<TranslationMatch<T>> = suggest(text, limit = 5)

    /**
     * Returns the single most similar entry, or null when even that is below [minSimilarity]. Entries that tie on
     * similarity are not ambiguous: the one with the lowest key wins. [layouts] work as in [suggest].
     */
    fun best(text: String, minSimilarity: Double, layouts: Collection<KeyboardLayout>): TranslationMatch<T>?

    /** Same as the three-argument form with this index's [layouts]. */
    fun best(text: String, minSimilarity: Double): TranslationMatch<T>? = best(text, minSimilarity, layouts.all())

    /** Same as the two-argument form with a similarity of 0.7. */
    fun best(text: String): TranslationMatch<T>? = best(text, minSimilarity = 0.7)

    /** Factories for [TranslationIndex]. */
    companion object {
        /**
         * Builds a reverse-search index from pre-resolved [matches]; used by platform modules to expose their own value
         * types. Pass the table's [LocaleTranslations.layouts] so that registered layouts apply to the index.
         */
        @JvmStatic
        @JvmOverloads
        fun <T> of(matches: List<TranslationMatch<T>>, layouts: KeyboardLayouts = KeyboardLayouts.builtIn): TranslationIndex<T> =
            TranslationIndexImpl(matches, layouts)
    }
}
