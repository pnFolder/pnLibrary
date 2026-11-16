package org.pnfolder.pnlibrary.localization.internal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.localization.TranslationMatch
import org.pnfolder.pnlibrary.localization.layout.KeyboardLayout
import org.pnfolder.pnlibrary.localization.layout.KeyboardLayouts
import java.lang.management.ManagementFactory
import java.util.Random
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

class TranslationSearchIndexRankingTest {
    private val words = listOf(
        "алмазный", "меч", "железный", "кирка", "деревянный", "топор", "золотой", "шлем", "блок", "камня", "руда", "дверь",
        "стекло", "панель", "ступени", "плита", "заготовка", "зелье", "свиток", "шерсть", "diamond", "sword", "iron", "pickaxe",
    )
    private val matches: List<TranslationMatch<String>> = Random(3).let { random ->
        (0 until 1500).map { index ->
            val text = (1..(1 + random.nextInt(3))).joinToString(" ") { words[random.nextInt(words.size)] }
            TranslationMatch("item.k$index", text, "item.k$index")
        }
    }
    private val layouts = KeyboardLayouts.builtIn.all()

    /** Queries typed exactly, with a typo, cut short, and in the wrong layout. */
    private fun queries(): List<String> {
        val random = Random(11)
        return (0 until 100).map { n ->
            val source = matches[random.nextInt(matches.size)].translation
            when (n % 4) {
                0 -> source
                1 -> source.replaceRange(1, 2, "ы")
                2 -> source.take(3 + random.nextInt(5))
                else -> KeyboardLayout.alternatives(source, layouts).firstOrNull() ?: source
            }
        }
    }

    @Test
    fun `suggest, fuzzy and best match the previous algorithm on 100 queries`() {
        val index = TranslationSearchIndex(matches, KeyboardLayouts.builtIn)
        val reference = ReferenceRanking(matches)
        queries().forEach { text ->
            val query = TranslationSearchIndex.normalize(text)
            listOf(emptyList(), layouts).forEach { chosen ->
                assertEquals(reference.suggest(index, query, 10, chosen).map { it.key }, index.suggest(text, 10, chosen).map { it.key }, text)
                assertEquals(reference.best(query, 0.7, chosen)?.key, index.best(text, 0.7, chosen)?.key, text)
            }
            assertEquals(reference.ranked(query, 0.6, emptyList()).take(5).map { it.key }, index.fuzzy(text, 0.6, 5).map { it.key }, text)
        }
    }

    private fun allocated(): Long =
        (ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean).getThreadAllocatedBytes(Thread.currentThread().id)

    @Test
    fun `scoring computes far fewer distances and allocates less than the previous algorithm`() {
        val counted = AtomicInteger()
        val index = TranslationSearchIndex(matches, KeyboardLayouts.builtIn) { a, b -> counted.incrementAndGet(); editDistance(a, b) }
        val reference = ReferenceRanking(matches)
        val queries = queries().map { TranslationSearchIndex.normalize(it) }.distinct()
        queries.forEach { index.suggest(it, 10, layouts) } // warm up classes, not the per-query results
        val fresh = TranslationSearchIndex(matches, KeyboardLayouts.builtIn) { a, b -> counted.incrementAndGet(); editDistance(a, b) }
        counted.set(0)

        val newStart = allocated()
        queries.forEach { fresh.suggest(it, 10, layouts) }
        val newBytes = allocated() - newStart
        val oldStart = allocated()
        queries.forEach { reference.suggest(fresh, it, 10, layouts) }
        val oldBytes = allocated() - oldStart

        assertTrue(counted.get() * 5 <= reference.distances, "distances new=${counted.get()} old=${reference.distances}")
        assertTrue(newBytes * 2 <= oldBytes, "allocated new=$newBytes old=$oldBytes")
    }

    @Test
    fun `a repeated suggestion is answered from the cache without scoring`() {
        val counted = AtomicInteger()
        val index = TranslationSearchIndex(matches, KeyboardLayouts.builtIn) { a, b -> counted.incrementAndGet(); editDistance(a, b) }
        val first = index.suggest("алмазн", 10, layouts)
        val after = counted.get()
        assertEquals(first, index.suggest("алмазн", 10, layouts))
        assertEquals(after, counted.get())
    }

    /** Previous algorithm kept verbatim to compare against. */
    internal class ReferenceRanking(matches: List<TranslationMatch<String>>) {
        private val entries = matches.map { it to TranslationSearchIndex.normalize(it.translation) }

        fun suggest(index: TranslationSearchIndex<String>, query: String, limit: Int, layouts: Collection<KeyboardLayout>) =
            (index.search(query) + ranked(query, 0.5, layouts)).distinctBy { it.key }.take(limit)

        fun best(text: String, floor: Double, layouts: Collection<KeyboardLayout>) =
            TranslationSearchIndex.normalize(text).let { ranked(it, floor, layouts).firstOrNull() }

        fun ranked(query: String, floor: Double, layouts: Collection<KeyboardLayout>): List<TranslationMatch<String>> {
            val queries = (listOf(query) + KeyboardLayout.alternatives(query, layouts).map { TranslationSearchIndex.normalize(it) }).distinct()
            return entries.mapNotNull { (match, normalized) ->
                val candidates = (listOf(normalized) + normalized.split(' ')).distinct()
                val scores = queries.map { variant -> candidates.maxOf { similarity(variant, it, floor) } }
                val score = scores.max()
                if (score >= floor) Triple(match, score, scores[0] >= score) else null
            }.sortedWith(compareByDescending<Triple<TranslationMatch<String>, Double, Boolean>> { it.second }
                .thenByDescending { it.third }.thenBy { it.first.key }).map { it.first }
        }

        private fun similarity(a: String, b: String, floor: Double): Double {
            val longest = maxOf(a.length, b.length)
            val edit = if (1.0 - abs(a.length - b.length).toDouble() / longest < floor) 0.0 else 1.0 - distance(a, b).toDouble() / longest
            if (a.length < 3 || b.length < 3) return edit
            val left = a.windowed(3).toSet()
            val right = b.windowed(3).toSet()
            return maxOf(edit, 2.0 * left.count { it in right } / (left.size + right.size))
        }

        var distances = 0

        private fun distance(a: String, b: String): Int {
            distances++
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
    }
}
