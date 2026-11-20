package org.pnfolder.pnlibrary.bukkit.item

/** A bounded, least-recently-used cache that stores and hands out copies, so a caller can never change what the cache holds. */
internal class CopyingCache<V : Any>(private val capacity: Int, private val copy: (V) -> V) {
    private val entries = object : LinkedHashMap<String, V>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, V>?): Boolean = size > capacity
    }

    val size: Int get() = synchronized(entries) { entries.size }

    operator fun get(key: String): V? = synchronized(entries) { entries[key] }?.let(copy)

    operator fun set(key: String, value: V) {
        val stored = copy(value)
        synchronized(entries) { entries[key] = stored }
    }
}
