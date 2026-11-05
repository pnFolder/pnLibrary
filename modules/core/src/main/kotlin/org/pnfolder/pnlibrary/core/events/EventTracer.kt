package org.pnfolder.pnlibrary.core.events

import org.pnfolder.pnlibrary.api.events.DispatchMode
import org.pnfolder.pnlibrary.api.events.Event
import org.pnfolder.pnlibrary.api.events.EventTrace
import org.pnfolder.pnlibrary.api.events.ResultedEvent
import java.time.Instant
import java.util.ArrayDeque

/** Keeps the recorded calls of the events an administrator switched tracing on for. */
internal class EventTracer {
    /** Traced event types and their capacity; empty almost always, so dispatch only reads one volatile field. */
    @Volatile private var traced: Map<Class<out Event>, Int> = emptyMap()

    /** Calls per traced type, so a type with a small capacity never pushes out the calls of another. */
    private val recorded = HashMap<Class<out Event>, ArrayDeque<EventTrace>>()

    fun enable(eventType: Class<out Event>, capacity: Int) {
        require(capacity in 1..1_000) { "Trace capacity must be between 1 and 1000" }
        synchronized(recorded) { traced = traced + (eventType to capacity) }
    }

    fun disable(eventType: Class<out Event>) {
        synchronized(recorded) { traced = traced - eventType }
    }

    fun tracedNames(): List<String> = traced.keys.map { it.name }.sorted()

    /** Every recorded call, oldest first. */
    fun traces(): List<EventTrace> = synchronized(recorded) { recorded.values.flatten() }.sortedBy { it.at }

    fun clear() {
        synchronized(recorded) { recorded.clear() }
    }

    /** The traced type that [type] is recorded under, or null when it is not traced. */
    fun tracedAs(type: Class<*>): Class<out Event>? {
        val current = traced
        if (current.isEmpty()) return null
        return current.keys.firstOrNull { it.isAssignableFrom(type) }
    }

    fun resultText(event: Event): String? = (event as? ResultedEvent<*>)?.result?.toString()

    fun record(tracedAs: Class<out Event>, event: Event, steps: List<EventTrace.Step>, mode: DispatchMode) {
        val trace = EventTrace(event.eventName, event.isAllowed, resultText(event), steps, mode, Thread.currentThread().name, Instant.now())
        synchronized(recorded) {
            val capacity = traced[tracedAs] ?: return
            val calls = recorded.getOrPut(tracedAs) { ArrayDeque() }
            calls.addLast(trace)
            while (calls.size > capacity) calls.removeFirst()
        }
    }
}
