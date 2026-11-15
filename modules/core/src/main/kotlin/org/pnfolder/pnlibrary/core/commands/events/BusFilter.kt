package org.pnfolder.pnlibrary.core.commands.events

import org.pnfolder.pnlibrary.api.events.EventInfo

/** A way to narrow the event list: the word typed after `/pn bus`, its label on the tab, and which events it keeps. */
internal enum class BusFilter(val word: String, val label: String, private val test: (EventInfo) -> Boolean) {
    ALL("all", "все", { true }),
    LISTENED("listened", "со слушателями", { it.listeners > 0 }),
    CALLED("called", "вызывались", { it.calls > 0 }),
    DENIED("denied", "с отказами", { it.denied > 0 }),
    ;

    fun keeps(event: EventInfo): Boolean = test(event)

    companion object {
        fun named(word: String): BusFilter = entries.first { it.word == word }
    }
}
