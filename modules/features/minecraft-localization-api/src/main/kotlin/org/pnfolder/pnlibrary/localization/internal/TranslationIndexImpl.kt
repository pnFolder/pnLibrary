package org.pnfolder.pnlibrary.localization.internal

import org.pnfolder.pnlibrary.localization.TranslationIndex
import org.pnfolder.pnlibrary.localization.TranslationMatch
import java.text.Normalizer
import java.util.Collections
import java.util.LinkedHashMap
import java.util.Locale

internal class TranslationIndexImpl<T>(matches: List<TranslationMatch<T>>) : TranslationIndex<T> {
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

    private data class Indexed<T>(val match: TranslationMatch<T>, val normalized: String)

    companion object {
        fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
            .trim().lowercase(Locale.ROOT).replace('ё', 'е').replace(WHITESPACE, " ")

        fun keys(values: Map<String, String>) = TranslationIndexImpl(
            values.map { (key, value) -> TranslationMatch(key, value, key) },
        )

        private val WHITESPACE = Regex("\\s+")
        private const val SEARCH_CACHE_CAPACITY = 64
    }
}
