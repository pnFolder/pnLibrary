package org.pnfolder.pnlibrary.core.link

import org.pnfolder.pnlibrary.core.link.report.LinkReport
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/** The newest report of every server that answered over the link; a newer report from the same name replaces the old one. */
class LinkRegistry {
    /** One server's report and when it arrived. */
    data class Entry(val name: String, val report: LinkReport, val receivedAt: Instant)

    private val entries = ConcurrentHashMap<String, Entry>()

    /** Stores [report] as the latest of [name]. */
    fun accept(name: String, report: LinkReport, now: Instant = Instant.now()) {
        entries[name] = Entry(name, report, now)
    }

    /** Every known server, sorted by name without regard to case. */
    fun all(): List<Entry> = entries.values.sortedBy { it.name.lowercase() }

    /** The server called [name], ignoring case, or `null`. */
    fun get(name: String): Entry? = entries.values.firstOrNull { it.name.equals(name, ignoreCase = true) }

    /** Forgets every server. */
    fun clear() = entries.clear()
}
