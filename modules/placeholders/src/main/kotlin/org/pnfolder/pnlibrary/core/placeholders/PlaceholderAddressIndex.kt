package org.pnfolder.pnlibrary.core.placeholders

import java.util.concurrent.ConcurrentHashMap

/** Finds placeholders by their readable `plugin/module:name` address. */
class PlaceholderAddressIndex {
    private val entries = ConcurrentHashMap<PlaceholderAddress, PlaceholderEntry<*>>()

    /** Adds [entry] under [address]; returns `false` when the address is taken. */
    fun add(address: PlaceholderAddress, entry: PlaceholderEntry<*>): Boolean = entries.putIfAbsent(address, entry) == null

    fun remove(address: PlaceholderAddress, entry: PlaceholderEntry<*>) {
        entries.remove(address, entry)
    }

    /** Every address with its entry, ordered for display. */
    fun all(): List<Pair<PlaceholderAddress, PlaceholderEntry<*>>> =
        entries.entries.map { it.key to it.value }.sortedBy { it.first.toString() }

    /** Finds the exact entry, or the most specific pattern of the same plugin and module. */
    fun find(address: PlaceholderAddress): PlaceholderEntryLookup.Match =
        search(address.name) { known -> known.plugin == address.plugin && known.module == address.module }

    /** Finds [name] in any module of [plugin]; the module `main` comes first, the rest by name. */
    fun findInPlugin(plugin: String, name: String): PlaceholderEntryLookup.Match =
        search(name) { known -> known.plugin == plugin }

    private fun search(name: String, inScope: (PlaceholderAddress) -> Boolean): PlaceholderEntryLookup.Match {
        val candidates = entries.entries.filter { inScope(it.key) }.sortedWith(compareBy({ it.key.module != MAIN }, { it.key.module }))
        val lowered = name.lowercase()
        candidates.firstOrNull { it.key.name == lowered }?.let { return PlaceholderEntryLookup.Match(it.value, emptyMap()) }
        return candidates
            .mapNotNull { (known, entry) -> PlaceholderPatternMatcher.match(known.name, name)?.let { Triple(known, entry, it) } }
            .maxByOrNull { (known, _, _) -> PlaceholderPatternMatcher.specificity(known.name) }
            ?.let { (_, entry, parameters) -> PlaceholderEntryLookup.Match(entry, parameters) }
            ?: PlaceholderEntryLookup.Match(null, emptyMap())
    }

    private companion object {
        const val MAIN = PlaceholderAddress.DEFAULT_MODULE
    }
}
