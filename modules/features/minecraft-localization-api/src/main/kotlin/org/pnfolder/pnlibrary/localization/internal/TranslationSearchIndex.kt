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

/**
 * Search index over translated names: exact lookup, search and fuzzy matching that also
 * tolerates text typed in a different keyboard layout.
 */
internal class TranslationSearchIndex<T>(
    matches: List<TranslationMatch<T>>,
    override val layouts: KeyboardLayouts = KeyboardLayouts.builtIn,
    private val distance: (String, String) -> Int = ::editDistance,
) : TranslationIndex<T> {
    /** All indexed translations with their normalized text. */
    private val entries = matches.map { Indexed(it, normalize(it.translation)) }
    /** Normalized text to its matches, for [findExact]. */
    private val exact = entries.groupBy({ it.normalized }, { it.match })
        .mapValues { (_, matches) -> Collections.unmodifiableList(matches) }
    /** Small LRU of ranked results per query, floor and layouts, so a repeated suggestion does no scoring; guarded by its own monitor. */
    private val rankedCache = object : LinkedHashMap<RankedKey, List<Ranked<T>>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<RankedKey, List<Ranked<T>>>?): Boolean =
            size > SEARCH_CACHE_CAPACITY
    }
    /** Small LRU of substring-search results; guarded by its own monitor. */
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
        val key = RankedKey(query, floor, layouts.toList())
        synchronized(rankedCache) { rankedCache[key]?.let { return it } }
        val result = computeRanked(query, floor, layouts)
        if (result.size <= MAX_CACHED_RANKED) synchronized(rankedCache) { rankedCache[key] = result }
        return result
    }

    private fun computeRanked(query: String, floor: Double, layouts: Collection<KeyboardLayout>): List<Ranked<T>> {
        val probes = (listOf(query) + KeyboardLayout.alternatives(query, layouts).map { normalize(it) }).distinct().map(::Probe)
        return entries.mapNotNull { entry ->
            var typedAsIs = 0.0
            var best = 0.0
            probes.forEachIndexed { index, probe ->
                val score = entry.candidates.maxOf { similarity(probe, it, floor) }
                if (index == 0) typedAsIs = score
                best = maxOf(best, score)
            }
            if (best >= floor) Ranked(entry.match, best, typedAsIs = typedAsIs >= best) else null
        }.sortedWith(compareByDescending<Ranked<T>> { it.score }.thenByDescending { it.typedAsIs }.thenBy { it.match.key })
    }

    /**
     * The better of the edit and letter-triple scores, both between 0.0 and 1.0. A pair that cannot reach [floor] may get any
     * value below it; the edit distance is only computed when the triple overlap leaves it a chance to win.
     */
    private fun similarity(probe: Probe, candidate: Candidate, floor: Double): Double {
        val a = probe.text
        val b = candidate.text
        val longest = maxOf(a.length, b.length)
        val lengthGap = abs(a.length - b.length)
        val lengthBound = 1.0 - lengthGap.toDouble() / longest
        if (a.length < 3 || b.length < 3) return if (lengthBound < floor) 0.0 else editScore(a, b, longest)
        val left = probe.trigrams
        val right = candidate.trigrams
        val common = left.count { it in right }
        val triple = 2.0 * common / (left.size + right.size)
        if (lengthBound < floor) return triple
        // One edit spoils at most four letter triples and changes at most two letter counts, so what differs bounds the distance from below.
        val unmatched = maxOf(left.size, right.size) - common
        val lowest = maxOf(lengthGap, (unmatched + 3) / 4, (letterDifference(probe.letters, candidate.letters) + 1) / 2)
        val bound = 1.0 - lowest.toDouble() / longest
        if (bound <= triple || bound < floor && triple < floor) return triple
        return maxOf(editScore(a, b, longest), triple)
    }

    /** Sum of the differences between how often each letter occurs in two sorted letter arrays. */
    private fun letterDifference(x: CharArray, y: CharArray): Int {
        var i = 0
        var j = 0
        var difference = 0
        while (i < x.size && j < y.size) {
            when {
                x[i] == y[j] -> { i++; j++ }
                x[i] < y[j] -> { i++; difference++ }
                else -> { j++; difference++ }
            }
        }
        return difference + (x.size - i) + (y.size - j)
    }

    private fun editScore(a: String, b: String, longest: Int) = 1.0 - distance(a, b).toDouble() / longest

    /** A match with its similarity [score]; [typedAsIs] is true when the query matched without layout conversion. */
    private class Ranked<T>(val match: TranslationMatch<T>, val score: Double, val typedAsIs: Boolean)

    /** A match with its normalized text; [candidates] are the whole text and each word, compared separately. */
    private class Indexed<T>(val match: TranslationMatch<T>, val normalized: String) {
        val candidates: List<Candidate> = (listOf(normalized) + normalized.split(' ')).distinct().map(::Candidate)
    }

    /** Text to compare against, with its letter triples computed on first use and then kept. */
    private class Candidate(val text: String) {
        val trigrams: Set<String> by lazy(LazyThreadSafetyMode.PUBLICATION) { trigramsOf(text) }
        val letters: CharArray by lazy(LazyThreadSafetyMode.PUBLICATION) { text.toCharArray().also { it.sort() } }
    }

    /** A query variant with its letter triples, computed once per search. */
    private class Probe(val text: String) {
        val trigrams: Set<String> = trigramsOf(text)
        val letters: CharArray = text.toCharArray().also { it.sort() }
    }

    private data class RankedKey(val query: String, val floor: Double, val layouts: List<KeyboardLayout>)

    companion object {
        /** Folds case, width and whitespace and treats `ё` as `е`, so lookups ignore those differences. */
        fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
            .trim().lowercase(Locale.ROOT).replace('ё', 'е').replace(WHITESPACE, " ")

        /** Builds an index whose results are keyed by the translation key itself. */
        fun keys(values: Map<String, String>, layouts: KeyboardLayouts) = TranslationSearchIndex(
            values.map { (key, value) -> TranslationMatch(key, value, key) },
            layouts,
        )

        /** Runs of whitespace collapsed by [normalize]. */
        private val WHITESPACE = Regex("\\s+")
        private const val SEARCH_CACHE_CAPACITY = 64
        private const val SUGGEST_FLOOR = 0.5
        private const val MAX_CACHED_RANKED = 2000
    }
}

private fun trigramsOf(text: String): Set<String> = if (text.length < 3) emptySet() else text.windowed(3).toSet()

/** Optimal string alignment distance: insertion, deletion, substitution and swapping two neighbours each cost 1. */
internal fun editDistance(a: String, b: String): Int {
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
