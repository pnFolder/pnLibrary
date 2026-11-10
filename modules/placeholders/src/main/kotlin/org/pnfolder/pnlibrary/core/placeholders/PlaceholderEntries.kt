package org.pnfolder.pnlibrary.core.placeholders

import org.pnfolder.pnlibrary.api.plugin.PluginId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/** Registered entries by `owner:key` id, with the parameterized ones also kept per owner so a pattern search skips everything else. */
class PlaceholderEntries {
    private val byId = ConcurrentHashMap<String, PlaceholderEntry<*>>()
    private val patternsByOwner = ConcurrentHashMap<PluginId, CopyOnWriteArrayList<PlaceholderEntry<*>>>()

    val values: Collection<PlaceholderEntry<*>> get() = byId.values

    operator fun get(id: String): PlaceholderEntry<*>? = byId[id]

    /** Adds [entry] under [id]; returns `false` when the id is taken. */
    fun add(id: String, entry: PlaceholderEntry<*>): Boolean {
        if (byId.putIfAbsent(id, entry) != null) return false
        if (entry.pattern != null) patternsByOwner.computeIfAbsent(entry.owner) { CopyOnWriteArrayList() } += entry
        return true
    }

    /** Puts [entry] under [id], replacing what was there. */
    operator fun set(id: String, entry: PlaceholderEntry<*>) {
        remove(id)
        add(id, entry)
    }

    /** Removes [entry] from [id]; a newer entry registered there is left alone. */
    fun remove(id: String, entry: PlaceholderEntry<*>): Boolean {
        if (!byId.remove(id, entry)) return false
        forgetPattern(entry)
        return true
    }

    fun remove(id: String): PlaceholderEntry<*>? = byId.remove(id)?.also(::forgetPattern)

    /** The parameterized entries of [owner], in registration order. */
    fun patternsOf(owner: PluginId): List<PlaceholderEntry<*>> = patternsByOwner[owner].orEmpty()

    private fun forgetPattern(entry: PlaceholderEntry<*>) {
        if (entry.pattern != null) patternsByOwner[entry.owner]?.remove(entry)
    }

    companion object {
        fun of(entries: Map<String, PlaceholderEntry<*>>) = PlaceholderEntries().also { index ->
            entries.forEach { (id, entry) -> index.add(id, entry) }
        }
    }
}
