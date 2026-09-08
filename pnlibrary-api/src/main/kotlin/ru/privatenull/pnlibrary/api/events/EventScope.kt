package ru.privatenull.pnlibrary.api.events

import ru.privatenull.pnlibrary.api.plugin.PluginId
import java.util.function.Consumer
import java.util.concurrent.CompletableFuture

/**
 * Plugin-ID-bound access to the pnLibrary event bus.
 *
 * Closing the scope removes every listener registered through it.
 */
interface EventScope : AutoCloseable {
    /** Plugin identity that owns every subscription in this scope. */
    val pluginId: PluginId
    val isClosed: Boolean

    /**
     * Discovers and registers every [EventHandler] method on [listener].
     * Invalid handler signatures fail immediately during registration.
     */
    fun register(listener: Listener): EventListenerRegistration

    /** Registers [listener] for [eventType] using the default priority. */
    fun <E : Event> subscribe(eventType: Class<E>, listener: Consumer<E>): EventSubscription =
        subscribe(eventType, EventPriority.NORMAL, false, listener)

    /** Registers [listener] at [priority]. */
    fun <E : Event> subscribe(
        eventType: Class<E>,
        priority: Int,
        listener: Consumer<E>,
    ): EventSubscription = subscribe(eventType, priority, false, listener)

    /**
     * Registers a listener with complete dispatch options.
     *
     * A listener also receives subclasses and implementations of [eventType].
     * Set [ignoreCancelled] to skip an event implementing [Cancellable] after cancellation.
     */
    fun <E : Event> subscribe(
        eventType: Class<E>,
        priority: Int,
        ignoreCancelled: Boolean,
        listener: Consumer<E>,
    ): EventSubscription

    /** Schedules [event] in its declared execution mode. */
    fun publish(event: Event): CompletableFuture<EventDispatchResult>

    /** Removes every listener registered through this scope. */
    override fun close()
}
