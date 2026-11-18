package org.pnfolder.pnlibrary.core.link

import org.pnfolder.pnlibrary.core.link.report.LinkReport
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class LinkRegistry {
    data class Entry(val name: String, val report: LinkReport, val receivedAt: Instant)

    private val entries = ConcurrentHashMap<String, Entry>()

    fun accept(name: String, report: LinkReport, now: Instant = Instant.now()) {
        entries[name] = Entry(name, report, now)
    }

    fun all(): List<Entry> = entries.values.sortedBy { it.name.lowercase() }

    fun get(name: String): Entry? = entries.values.firstOrNull { it.name.equals(name, ignoreCase = true) }

    fun clear() = entries.clear()
}
