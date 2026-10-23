package org.pnfolder.pnlibrary.localization.internal

import org.pnfolder.pnlibrary.localization.TranslationIndex
import org.pnfolder.pnlibrary.localization.TranslationMatch
import org.pnfolder.pnlibrary.localization.layout.KeyboardLayout
import org.pnfolder.pnlibrary.localization.layout.KeyboardLayouts
import java.text.Normalizer
import java.util.Collections
import java.util.LinkedHashMap
import java.util.Locale
import kotlin.math.abs

internal class TranslationIndexImpl<T>(
    matches: List<TranslationMatch<T>>,
    override val layouts: KeyboardLayouts = KeyboardLayouts.builtIn,
) : TranslationIndex<T> {
    private val entries = matches.map { Indexed(it, normalize(it.translation)) }
    private val exact = entries.groupBy({ it.normalized }, { it.match })
        .mapValues { (_, matches) -> Collections.unmodifiableList(matches) }
    private val searchCache = object : LinkedHashMap<String, List<TranslationMatch<T>>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<TranslationMatch<T>>>?): Boolean =
            size > SEARCH_CACHE_CAPACITY
    }

    override fun findExact(text: String): List<TranslationMatch<T>> {
        return exact[normalize(text)] ?: emptyList()
    }

    override fun search(text: String): List<TranslationMatch<T>> {
        val query = normalize(text)
        if (query.isEmpty()) return emptyList()
        synchronized(searchCache) {
            searchCache[query]?.let { return it }
        }
        val result = Collections.unmodifiableList(entries.asSequence().filter { query in it.normalized }
            .sortedWith(compareBy<Indexed<T>>(
                {
                    when {
                        it.normalized == query -> 0
                        it.normalized.startsWith(query) -> 1
                        else -> 2
                    }
                },
                { it.match.key },
            )).map { it.match }.toList())
        synchronized(searchCache) {
            searchCache[query] = result
        }
        return result
    }

    override fun fuzzy(text: String, minSimilarity: Double, limit: Int): List<TranslationMatch<T>> {
        require(minSimilarity in 0.0..1.0) { "minSimilarity must be within 0.0..1.0, was $minSimilarity" }
        require(limit > 0) { "limit must be positive, was $limit" }
        val query = normalize(text)
        if (query.isEmpty()) return emptyList()
        return ranked(query, minSimilarity, emptyList()).take(limit).map { it.match }
    }

    override fun suggest(text: String, limit: Int, layouts: Collection<KeyboardLayout>): List<TranslationMatch<T>> {
        require(limit > 0) { "limit must be positive, was $limit" }
        val query = normalize(text)
        if (query.isEmpty()) return emptyList()
        return (search(query) + ranked(query, SUGGEST_FLOOR, layouts).map { it.match }).distinctBy { it.key }.take(limit)
    }

    override fun best(text: String, minSimilarity: Double, layouts: Collection<KeyboardLayout>): TranslationMatch<T>? {
        require(minSimilarity in 0.0..1.0) { "minSimilarity must be within 0.0..1.0, was $minSimilarity" }
        val query = normalize(text)
        if (query.isEmpty()) return null
        return ranked(query, minSimilarity, layouts).firstOrNull()?.match
    }

    /**
     * Entries scoring at least [floor] against [query] or any re-reading of it on another of [layouts]; most similar
     * first, then those the query matched as typed, then by key.
     */
    private fun ranked(query: String, floor: Double, layouts: Collection<KeyboardLayout>): List<Ranked<T>> {
        val queries = (listOf(query) + KeyboardLayout.alternatives(query, layouts).map { normalize(it) }).distinct()
        return entries.mapNotNull { entry ->
            val scores = queries.map { variant -> entry.candidates.maxOf { similarity(variant, it, floor) } }
            val score = scores.max()
            if (score >= floor) Ranked(entry.match, score, typedAsIs = scores[0] >= score) else null
        }.sortedWith(compareByDescending<Ranked<T>> { it.score }.thenByDescending { it.typedAsIs }.thenBy { it.match.key })
    }

    private class Ranked<T>(val match: TranslationMatch<T>, val score: Double, val typedAsIs: Boolean)

    private class Indexed<T>(val match: TranslationMatch<T>, val normalized: String) {
        val candidates: List<String> = (listOf(normalized) + normalized.split(' ')).distinct()
    }

    companion object {
        fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
            .trim().lowercase(Locale.ROOT).replace('ё', 'е').replace(WHITESPACE, " ")

        fun keys(values: Map<String, String>, layouts: KeyboardLayouts) = TranslationIndexImpl(
            values.map { (key, value) -> TranslationMatch(key, value, key) },
            layouts,
        )

        private val WHITESPACE = Regex("\\s+")
        private const val SEARCH_CACHE_CAPACITY = 64
        private const val SUGGEST_FLOOR = 0.5
    }
}

/** The better of the edit and letter-triple scores, both between 0.0 and 1.0. */
private fun similarity(a: String, b: String, floor: Double): Double {
    val longest = maxOf(a.length, b.length)
    val edit = if (1.0 - abs(a.length - b.length).toDouble() / longest < floor) 0.0 else 1.0 - distance(a, b).toDouble() / longest
    if (a.length < 3 || b.length < 3) return edit
    val left = a.windowed(3).toSet()
    val right = b.windowed(3).toSet()
    return maxOf(edit, 2.0 * left.count { it in right } / (left.size + right.size))
}

/** Optimal string alignment distance: insertion, deletion, substitution and swapping two neighbours each cost 1. */
private fun distance(a: String, b: String): Int {
    var older = IntArray(b.length + 1)
    var previous = IntArray(b.length + 1) { it }
    var current = IntArray(b.length + 1)
    for (i in 1..a.length) {
        current[0] = i
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            var best = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
            if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) best = minOf(best, older[j - 2] + 1)
            current[j] = best
        }
        val recycled = older
        older = previous
        previous = current
        current = recycled
    }
    return previous[b.length]
}
