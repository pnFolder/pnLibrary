package org.pnfolder.pnlibrary.bukkit.placeholders

import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * The names published to PlaceholderAPI under one prefix, and the lookup of which one PlaceholderAPI's text
 * (`war~score.Alex`, `tag_x`) belongs to. Lookups read an immutable snapshot, so they never wait for a publication.
 */
internal class PublishedNames<H> {
    /** A found name with the values of its brace parts. */
    data class Found<H>(val handler: H, val parameters: Map<String, String>)

    private class Snapshot<H>(val names: Map<String, H>) {
        /** Plain names by lowercase text for the direct hit. */
        val exact: Map<String, H> = names.filterKeys { '{' !in it }.mapKeys { it.key.lowercase(Locale.ROOT) }
        val patterns: List<Pair<String, H>> = names.filterKeys { '{' in it }.toList()
        val specificity: Map<String, Int> = patterns.associate { (name, _) -> name to ExternalNames.specificity(name) }

        /** Longest plain name first, for the "name, underscore, rest" form. */
        val byLength: List<Pair<String, H>> = names.filterKeys { '{' !in it }.toList().sortedByDescending { it.first.length }
    }

    private val scannedCounter = AtomicLong()

    @Volatile
    private var snapshot = Snapshot<H>(emptyMap())

    /** How many names were tried one by one since start; a direct hit tries none. */
    val scanned: Long get() = scannedCounter.get()

    val isEmpty: Boolean get() = snapshot.names.isEmpty()

    /** Publishes [handler] under [name]; `false` when the name is taken. */
    @Synchronized
    fun add(name: String, handler: H): Boolean {
        if (name in snapshot.names) return false
        snapshot = Snapshot(snapshot.names + (name to handler))
        return true
    }

    /** Withdraws [name]. */
    @Synchronized
    fun remove(name: String) {
        if (name in snapshot.names) snapshot = Snapshot(snapshot.names - name)
    }

    /** The name that equals [params], then the most specific pattern that fits, otherwise the longest plain name that [params] equals or continues after `_`. */
    fun find(params: String): Found<H>? {
        val current = snapshot
        current.exact[params.lowercase(Locale.ROOT)]?.let { return Found(it, emptyMap()) }
        return byPattern(current, params) ?: byPrefix(current, params)
    }

    private fun byPattern(current: Snapshot<H>, params: String): Found<H>? {
        var best: Found<H>? = null
        var bestSpecificity = -1
        for ((name, handler) in current.patterns) {
            scannedCounter.incrementAndGet()
            val parameters = ExternalNames.match(name, params) ?: continue
            val specificity = current.specificity.getValue(name)
            if (specificity > bestSpecificity) {
                best = Found(handler, parameters)
                bestSpecificity = specificity
            }
        }
        return best
    }

    private fun byPrefix(current: Snapshot<H>, params: String): Found<H>? {
        for ((name, handler) in current.byLength) {
            scannedCounter.incrementAndGet()
            if (!params.equals(name, true) && !params.startsWith(name + "_", true)) continue
            val suffix = params.removePrefix(name).removePrefix("_")
            return Found(handler, if (suffix.isEmpty()) emptyMap() else mapOf("value" to suffix))
        }
        return null
    }
}
