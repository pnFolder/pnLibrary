package ru.privatenull.pnlibrary.api.events

import java.util.function.Consumer

/**
 * Owner-bound access to the pnLibrary event bus.
 *
 * Closing the scope removes every listener registered through it. Dispatch is
 * synchronous and happens on the thread that calls [publish].
 */
interface EventScope : AutoCloseable {
    val owner: Any
    val isClosed: Boolean

    /**
     * Discovers and registers every [HandlesEvent] method on [listener].
     * Invalid handler signatures fail immediately during registration.
     */
    fun register(listener: EventSubscriber): EventListenerRegistration

    /** Registers [listener] for [eventType] using the default priority. */
    fun <E : LibraryEvent> subscribe(eventType: Class<E>, listener: Consumer<E>): EventSubscription =
        subscribe(eventType, EventPriority.NORMAL, false, listener)

    /** Registers [listener] at [priority]. */
    fun <E : LibraryEvent> subscribe(
        eventType: Class<E>,
        priority: Int,
        listener: Consumer<E>,
    ): EventSubscription = subscribe(eventType, priority, false, listener)

    /**
     * Registers a listener with complete dispatch options.
     *
     * A listener also receives subclasses and implementations of [eventType].
     * Set [ignoreCancelled] to skip a [CancellableEvent] after cancellation.
     */
    fun <E : LibraryEvent> subscribe(
        eventType: Class<E>,
        priority: Int,
        ignoreCancelled: Boolean,
        listener: Consumer<E>,
    ): EventSubscription

    /** Publishes [event] synchronously to every matching listener. */
    fun publish(event: LibraryEvent): EventDispatchResult

    /** Removes every listener registered through this scope. */
    override fun close()
}
