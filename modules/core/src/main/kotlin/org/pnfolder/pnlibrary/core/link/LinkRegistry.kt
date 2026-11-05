package org.pnfolder.pnlibrary.core.link

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/** The game servers a proxy has heard from, by the name the proxy knows them under. */
class LinkRegistry {
    data class Entry(val name: String, val report: LinkReport, val receivedAt: Instant)

    private val entries = ConcurrentHashMap<String, Entry>()

    fun accept(name: String, report: LinkReport, now: Instant = Instant.now()) {
        entries[name] = Entry(name, report, now)
    }

    fun all(): List<Entry> = entries.values.sortedBy { it.name.lowercase() }

    /** The server called [name], ignoring case, or null. */
    fun get(name: String): Entry? = entries.values.firstOrNull { it.name.equals(name, ignoreCase = true) }

    fun clear() = entries.clear()
}
