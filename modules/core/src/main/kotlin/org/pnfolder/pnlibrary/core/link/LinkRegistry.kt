package org.pnfolder.pnlibrary.core.link

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/** The game servers a proxy has heard from, by the name the proxy knows them under. */
class LinkRegistry {
    /** The latest report of one server and when it arrived. */
    data class Entry(val name: String, val report: LinkReport, val receivedAt: Instant)

    private val entries = ConcurrentHashMap<String, Entry>()

    /** Remembers [report] for the server called [name]. */
    fun accept(name: String, report: LinkReport, now: Instant = Instant.now()) {
        entries[name] = Entry(name, report, now)
    }

    /** Every known server, by name. */
    fun all(): List<Entry> = entries.values.sortedBy { it.name.lowercase() }

    /** The server called [name], ignoring case, or null. */
    fun get(name: String): Entry? = entries.values.firstOrNull { it.name.equals(name, ignoreCase = true) }

    /** Forgets every server. */
    fun clear() = entries.clear()
}

/** The one registry of this process: the platform listeners fill it and `/pn servers` reads it. */
object LinkHub {
    /** Servers heard from since the proxy started. */
    val registry = LinkRegistry()

    /** The latest network map this game server received from its proxy, or null when none has arrived. */
    @Volatile
    var network: ReceivedNetwork? = null

    /** A network map and when it arrived. */
    data class ReceivedNetwork(val report: NetworkReport, val receivedAt: Instant)
}
