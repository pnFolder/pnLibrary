package org.pnfolder.pnlibrary.core.events

import org.pnfolder.pnlibrary.api.events.DispatchMode
import org.pnfolder.pnlibrary.api.events.Event
import org.pnfolder.pnlibrary.api.events.EventTrace
import org.pnfolder.pnlibrary.api.events.ResultedEvent

/** Keeps the recorded calls of the events an administrator switched tracing on for. */
internal class EventTracer {
    /** Traced event types and their capacity; empty almost always, so dispatch only reads one volatile field. */
    @Volatile private var traced: Map<Class<out Event>, Int> = emptyMap()
    private val recorded = java.util.ArrayDeque<EventTrace>()

    fun enable(eventType: Class<out Event>, capacity: Int) {
        require(capacity in 1..1_000) { "Trace capacity must be between 1 and 1000" }
        synchronized(recorded) { traced = traced + (eventType to capacity) }
    }

    fun disable(eventType: Class<out Event>) {
        synchronized(recorded) { traced = traced - eventType }
    }

    fun tracedNames(): List<String> = traced.keys.map { it.name }.sorted()

    fun traces(): List<EventTrace> = synchronized(recorded) { recorded.toList() }

    fun clear() {
        synchronized(recorded) { recorded.clear() }
    }

    /** How many calls to keep for events of [type], or null when none is traced. */
    fun capacity(type: Class<*>): Int? =
        traced.takeIf { it.isNotEmpty() }?.entries?.firstOrNull { it.key.isAssignableFrom(type) }?.value

    fun resultText(event: Event): String? = (event as? ResultedEvent<*>)?.result?.toString()

    fun record(event: Event, steps: List<EventTrace.Step>, capacity: Int, mode: DispatchMode) {
        val trace = EventTrace(event.eventName, event.isAllowed, resultText(event), steps, mode, Thread.currentThread().name, java.time.Instant.now())
        synchronized(recorded) {
            recorded.addLast(trace)
            while (recorded.size > capacity) recorded.removeFirst()
        }
    }
}
