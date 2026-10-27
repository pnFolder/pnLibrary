package org.pnfolder.pnlibrary.api.events

import org.pnfolder.pnlibrary.api.plugin.ModuleId
import org.pnfolder.pnlibrary.api.plugin.PluginId

/**
 * Read-only view of the event bus: who listens, which events were called and what happened to them.
 *
 * Obtained from [EventService.inspector]. Everything here works before an event has ever been called.
 *
 * ```kotlin
 * val bus = PnLibraryProvider.get().events.inspector
 * bus.listenerCount(PurchaseEvent::class.java)
 * bus.listeners(plugin = PluginId.of("shop")).forEach { println("${it.module} ${it.source}") }
 * ```
 */
interface EventInspector {
    /**
     * Lists the listeners that would run for [eventType], in dispatch order. Works before the event has
     * ever been called, so it also answers who is subscribed to an event nobody has fired yet.
     */
    fun listeners(eventType: Class<out Event>): List<ListenerInfo>

    /** Lists every registered listener of every plugin, ordered by event type and then dispatch order. */
    fun listeners(): List<ListenerInfo>

    /** Lists the listeners of one [plugin], optionally only those of one [module]. */
    fun listeners(plugin: PluginId, module: ModuleId? = null): List<ListenerInfo> =
        listeners().filter { it.plugin == plugin && (module == null || it.module == module) }

    /** Resolves the class of a known event by its full class name, or `null` when it is not known. */
    fun eventType(name: String): Class<out Event>?

    /** Number of listeners that would run for [eventType]. */
    fun listenerCount(eventType: Class<out Event>): Int = listeners(eventType).size

    /** Plugins that have at least one listener for [eventType], in dispatch order without repeats. */
    fun plugins(eventType: Class<out Event>): List<PluginId> = listeners(eventType).map { it.plugin }.distinct()

    /**
     * Every event type that has a listener or has been called, with counters, ordered by name.
     * Call counters are kept for the whole process lifetime.
     */
    fun events(): List<EventInfo>

    /** The entry of one event type, or `null` when nobody listens to it and it was never called. */
    fun event(eventType: Class<out Event>): EventInfo? = events().firstOrNull { it.eventType == eventType.name }

    /**
     * Per-day call counts, newest day first, for [eventType] or for every event when it is `null`. The history is
     * kept for [historyDays] days, set by `eventHistoryDays` in the library settings, and survives restarts.
     */
    fun history(eventType: String? = null): List<EventDay>

    /** How many days [history] reaches back; `0` means no history is kept. */
    val historyDays: Int

    /**
     * Starts recording every dispatch of [eventType] and its subclasses, keeping the latest
     * [EventTrace]s up to [capacity]. Tracing costs nothing for event types that are not traced.
     * Results are compared through `toString()`, so give a custom [ResultedEvent.Result] a readable one.
     */
    fun enableTracing(eventType: Class<out Event>, capacity: Int = 32)

    /** Stops recording [eventType]; recorded traces stay available until [clearTraces]. */
    fun disableTracing(eventType: Class<out Event>)

    /** Event type names that are being traced now. */
    fun tracedEvents(): List<String>

    /** Recorded dispatches, oldest first. */
    fun traces(): List<EventTrace>

    /** Forgets every recorded trace. */
    fun clearTraces()
}
