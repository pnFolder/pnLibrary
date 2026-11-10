package org.pnfolder.pnlibrary.core.placeholders

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/** Finds placeholders by their readable `plugin/module:name` address. */
class PlaceholderAddressIndex {
    private class Registered(val address: PlaceholderAddress, val entry: PlaceholderEntry<*>)

    /** Everything one module registered: exact names for direct lookup, parameterized ones kept apart for the pattern search. */
    private class ModuleSlots {
        val exact = ConcurrentHashMap<String, Registered>()
        val patterns = CopyOnWriteArrayList<Registered>()
    }

    private val modulesByPlugin = ConcurrentHashMap<String, ConcurrentHashMap<String, ModuleSlots>>()

    /** Adds [entry] under [address]; returns `false` when the address is taken. */
    fun add(address: PlaceholderAddress, entry: PlaceholderEntry<*>): Boolean {
        val slots = modulesByPlugin.computeIfAbsent(address.plugin) { ConcurrentHashMap() }
            .computeIfAbsent(address.module) { ModuleSlots() }
        val registered = Registered(address, entry)
        if (slots.exact.putIfAbsent(address.name, registered) != null) return false
        if (entry.pattern != null) slots.patterns += registered
        return true
    }

    /** Removes [entry] from [address]; a newer entry registered there is left alone. */
    fun remove(address: PlaceholderAddress, entry: PlaceholderEntry<*>) {
        val slots = modulesByPlugin[address.plugin]?.get(address.module) ?: return
        val registered = slots.exact[address.name]?.takeIf { it.entry === entry } ?: return
        if (slots.exact.remove(address.name, registered)) slots.patterns.remove(registered)
    }

    /** Every address with its entry, ordered for display. */
    fun all(): List<Pair<PlaceholderAddress, PlaceholderEntry<*>>> =
        modulesByPlugin.values.flatMap { modules -> modules.values.flatMap { it.exact.values } }
            .map { it.address to it.entry }
            .sortedBy { it.first.toString() }

    /** Finds the exact entry, or the most specific pattern of the same plugin and module. */
    fun find(address: PlaceholderAddress): PlaceholderEntryLookup.Match {
        val slots = modulesByPlugin[address.plugin]?.get(address.module) ?: return NOTHING
        return search(listOf(slots), address.name)
    }

    /** Finds [name] in any module of [plugin]; the module `main` comes first, the rest by name. */
    fun findInPlugin(plugin: String, name: String): PlaceholderEntryLookup.Match {
        val modules = modulesByPlugin[plugin] ?: return NOTHING
        val ordered = modules.entries.sortedWith(compareBy({ it.key != MAIN }, { it.key })).map { it.value }
        return search(ordered, name)
    }

    private fun search(scope: List<ModuleSlots>, name: String): PlaceholderEntryLookup.Match {
        val lowered = name.lowercase()
        for (slots in scope) {
            slots.exact[lowered]?.let { return PlaceholderEntryLookup.Match(it.entry, emptyMap()) }
        }
        var best: PlaceholderEntry<*>? = null
        var bestParameters = emptyMap<String, String>()
        for (slots in scope) {
            for (registered in slots.patterns) {
                val pattern = registered.entry.pattern ?: continue
                val parameters = pattern.match(name) ?: continue
                if (best == null || pattern.specificity > best.pattern!!.specificity) {
                    best = registered.entry
                    bestParameters = parameters
                }
            }
        }
        return if (best == null) NOTHING else PlaceholderEntryLookup.Match(best, bestParameters)
    }

    private companion object {
        const val MAIN = PlaceholderAddress.DEFAULT_MODULE
        val NOTHING = PlaceholderEntryLookup.Match(null, emptyMap())
    }
}
