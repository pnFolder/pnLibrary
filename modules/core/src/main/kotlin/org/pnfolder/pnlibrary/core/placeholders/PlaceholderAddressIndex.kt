package org.pnfolder.pnlibrary.core.placeholders

import java.util.concurrent.ConcurrentHashMap

/** Finds placeholders by their readable `plugin/module:name` address. */
internal class PlaceholderAddressIndex {
    private val entries = ConcurrentHashMap<PlaceholderAddress, PlaceholderEntry<*>>()

    /** Adds [entry] under [address]; returns `false` when the address is taken. */
    fun add(address: PlaceholderAddress, entry: PlaceholderEntry<*>): Boolean = entries.putIfAbsent(address, entry) == null

    fun remove(address: PlaceholderAddress, entry: PlaceholderEntry<*>) {
        entries.remove(address, entry)
    }

    /** Finds the exact entry, or the most specific pattern of the same plugin and module. */
    fun find(address: PlaceholderAddress): PlaceholderEntryLookup.Match {
        entries[address.copy(name = address.name.lowercase())]?.let { return PlaceholderEntryLookup.Match(it, emptyMap()) }
        return entries.entries
            .asSequence()
            .filter { (known, _) -> known.plugin == address.plugin && known.module == address.module }
            .mapNotNull { (known, entry) ->
                PlaceholderPatternMatcher.match(known.name, address.name)?.let { Triple(known, entry, it) }
            }
            .maxByOrNull { (known, _, _) -> known.name.length }
            ?.let { (_, entry, parameters) -> PlaceholderEntryLookup.Match(entry, parameters) }
            ?: PlaceholderEntryLookup.Match(null, emptyMap())
    }
}
