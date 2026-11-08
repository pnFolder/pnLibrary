package org.pnfolder.pnlibrary.core.link

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/** The game servers a proxy has heard from, by the name the proxy knows them under. */
class LinkRegistry {
    /** One server's latest report and when it arrived. */
    data class Entry(val name: String, val report: LinkReport, val receivedAt: Instant)

    private val entries = ConcurrentHashMap<String, Entry>()

    /** Remembers [report] as the latest one from the server called [name]. */
    fun accept(name: String, report: LinkReport, now: Instant = Instant.now()) {
        entries[name] = Entry(name, report, now)
    }

    /** All servers heard from, sorted by name ignoring case. */
    fun all(): List<Entry> = entries.values.sortedBy { it.name.lowercase() }

    /** The server called [name], ignoring case, or null. */
    fun get(name: String): Entry? = entries.values.firstOrNull { it.name.equals(name, ignoreCase = true) }

    /** Forgets every server. */
    fun clear() = entries.clear()
}
