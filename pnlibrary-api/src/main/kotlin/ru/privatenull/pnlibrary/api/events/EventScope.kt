package ru.privatenull.pnlibrary.api.events

import ru.privatenull.pnlibrary.api.plugin.PluginId
import java.util.concurrent.CompletionStage
import java.util.function.Consumer

/**
 * Plugin-ID-bound access to the pnLibrary event bus.
 *
 * Closing the scope removes every listener registered through it. [publish]
 * runs inline; [publishAsync] uses the shared bounded event executor.
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

    /** Publishes a non-asynchronous [event] inline to every matching listener. */
    fun publish(event: Event): EventDispatchResult

    /** Publishes an asynchronous [event] on the event executor. */
    fun publishAsync(event: Event): CompletionStage<EventDispatchResult>

    /** Removes every listener registered through this scope. */
    override fun close()
}
