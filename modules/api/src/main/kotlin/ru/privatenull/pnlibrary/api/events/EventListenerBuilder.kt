package ru.privatenull.pnlibrary.api.events

import java.util.function.Consumer

/**
 * Fluent way to subscribe to one event type.
 *
 * ```
 * scope.on<PurchaseEvent>()
 *     .priority(EventPriority.HIGH)
 *     .ignoreCancelled()
 *     .listen { event -> ... }
 * ```
 *
 * Nothing is registered until [listen] is called. The builder itself holds no subscription.
 */
class EventListenerBuilder<E : Event> internal constructor(
    private val scope: EventScope,
    private val eventType: Class<E>,
) {
    private var priority: Int = EventPriority.NORMAL
    private var ignoreCancelled: Boolean = false

    /** Sets the dispatch order; lower numbers run first. Defaults to [EventPriority.NORMAL]. */
    fun priority(value: Int): EventListenerBuilder<E> = apply { priority = value }

    /** Skips this listener when the event is already denied. Off by default. */
    fun ignoreCancelled(): EventListenerBuilder<E> = apply { ignoreCancelled = true }

    /** Registers [listener] with the configured options and returns the live subscription. */
    fun listen(listener: Consumer<E>): EventSubscription =
        scope.subscribe(eventType, priority, ignoreCancelled, listener)
}

/** Starts building a subscription to [E] in this scope. */
inline fun <reified E : Event> EventScope.on(): EventListenerBuilder<E> = on(E::class.java)

/** Starts building a subscription to [eventType] in this scope; the Java-friendly entry point. */
fun <E : Event> EventScope.on(eventType: Class<E>): EventListenerBuilder<E> =
    EventListenerBuilder(this, eventType)
