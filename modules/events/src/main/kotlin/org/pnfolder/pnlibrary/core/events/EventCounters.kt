package org.pnfolder.pnlibrary.core.events

import org.pnfolder.pnlibrary.api.events.Event
import org.pnfolder.pnlibrary.api.events.EventInfo
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.LongAdder

/** Counts the calls and the denials of every event type, and passes each call to the history. */
class EventCounters(private val history: EventHistory?) {
    private class Counter(type: Class<*>) {
        val type = java.lang.ref.WeakReference(type)
        val calls = LongAdder()
        val denied = LongAdder()

        @Volatile var lastMillis = 0L
    }

    private val counters = ConcurrentHashMap<String, Counter>()

    fun count(event: Event) {
        val name = event.javaClass.name
        val counter = counters[name] ?: counters.computeIfAbsent(name) { Counter(event.javaClass) }
        counter.calls.increment()
        counter.lastMillis = System.currentTimeMillis()
        if (!event.isAllowed) counter.denied.increment()
        history?.record(name, !event.isAllowed)
    }

    /** The class of the event [name] if it was called and is still loaded. */
    fun type(name: String): Class<*>? = counters[name]?.type?.get()

    /** Every counted event name with its class, or null once the class was unloaded. */
    fun types(): List<Pair<String, Class<*>?>> = counters.map { (name, counter) -> name to counter.type.get() }

    /** The counters of [name] with the number of [listeners] it has now. */
    fun info(name: String, listeners: Int): EventInfo {
        val counter = counters[name]
        return EventInfo(
            name, listeners, counter?.calls?.sum() ?: 0, counter?.denied?.sum() ?: 0,
            counter?.lastMillis?.takeIf { it > 0 }?.let(java.time.Instant::ofEpochMilli),
        )
    }
}
