@file:Suppress("DEPRECATION")

package org.pnfolder.pnlibrary.core.events

import org.pnfolder.pnlibrary.api.events.Event
import org.pnfolder.pnlibrary.api.events.EventListenerRegistration
import org.pnfolder.pnlibrary.api.events.EventScope
import org.pnfolder.pnlibrary.api.events.EventService
import org.pnfolder.pnlibrary.api.events.EventSubscription
import org.pnfolder.pnlibrary.api.events.Listener
import org.pnfolder.pnlibrary.api.plugin.PluginId
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer

/** Thread-safe, mode-aware implementation of the platform-independent event bus. */
internal class EventServiceImpl(
    private val errorLogger: (PluginId, String, Throwable) -> Unit,
) : EventService {

    private val scopes = HashMap<PluginId, Scope>()
    private val subscriptions = CopyOnWriteArrayList<Subscription<out Event>>()
    private val sequence = AtomicLong()
    private val closed = AtomicBoolean(false)

    override fun scope(pluginId: PluginId): EventScope {
        return synchronized(scopes) {
            check(!closed.get()) { "EventService is closed" }
            scopes.getOrPut(pluginId) { Scope(pluginId) }
        }
    }

    override fun <E : Event> callEvent(event: E): E {
        check(!closed.get()) { "EventService is closed" }
        dispatchInline(event)
        return event
    }

    /**
     * Sorted listeners per event class. Any change to the subscriptions bumps [generation] and empties the
     * cache, so a lookup either hits a list built for the current subscriptions or rebuilds it. Weak keys let
     * an unloaded plugin's event classes be collected when nothing listens to them.
     */
    private class Cached(val generation: Long, val handlers: List<Subscription<out Event>>)
    private val generation = AtomicLong()
    private val cache = java.util.Collections.synchronizedMap(java.util.WeakHashMap<Class<*>, Cached>())

    private fun invalidateHandlers() {
        generation.incrementAndGet()
        cache.clear()
    }

    private fun handlersFor(type: Class<*>): List<Subscription<out Event>> {
        val current = generation.get()
        cache[type]?.let { if (it.generation == current) return it.handlers }
        val built = subscriptions.asSequence()
            .filter { !it.isClosed && it.eventType.isAssignableFrom(type) }
            .sortedWith(compareBy<Subscription<out Event>> { it.priority }.thenBy { it.order })
            .toList()
        cache[type] = Cached(current, built)
        return built
    }

    private fun dispatchInline(event: Event) {
        check(!closed.get()) { "EventService is closed" }
        val matching = handlersFor(event.javaClass)

        matching.forEach { subscription ->
            if (subscription.isClosed) return@forEach
            if (subscription.ignoreCancelled && !event.isAllowed) {
                return@forEach
            }
            try {
                subscription.invoke(event)
            } catch (error: Throwable) {
                errorLogger(
                    subscription.scope.pluginId,
                    "[pnLibrary/events] Listener failed for ${event.javaClass.name}",
                    error,
                )
            }
        }

    }

    override fun unregisterAll(pluginId: PluginId) {
        synchronized(scopes) { scopes.remove(pluginId) }?.closeInternal()
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        val current = synchronized(scopes) { scopes.values.toList().also { scopes.clear() } }
        current.forEach { it.closeInternal() }
        subscriptions.clear()
        invalidateHandlers()
    }

    private inner class Scope(override val pluginId: PluginId) : EventScope {
        private val scopeClosed = AtomicBoolean(false)
        private val ownedSubscriptions = CopyOnWriteArrayList<Subscription<out Event>>()
        override val isClosed: Boolean get() = scopeClosed.get()

        override fun register(listener: Listener): EventListenerRegistration {
            check(!closed.get() && !scopeClosed.get()) { "EventScope is closed" }
            val handlers = EventHandlerInspector.inspect(listener)
            require(handlers.isNotEmpty()) {
                "${listener.javaClass.name} does not declare any @EventHandler methods"
            }

            val registered = mutableListOf<EventSubscription>()
            try {
                handlers.forEach { handler ->
                    registered += subscribeHandler(listener, handler)
                }
                return ListenerRegistration(listener, registered)
            } catch (error: Throwable) {
                registered.forEach { runCatching { it.close() } }
                throw error
            }
        }

        override fun <E : Event> subscribe(
            eventType: Class<E>,
            priority: Int,
            ignoreCancelled: Boolean,
            listener: Consumer<E>,
        ): EventSubscription {
            check(!closed.get() && !scopeClosed.get()) { "EventScope is closed" }
            val subscription = Subscription(
                scope = this,
                eventType = eventType,
                priority = priority,
                ignoreCancelled = ignoreCancelled,
                listener = listener,
                order = sequence.getAndIncrement(),
            )
            ownedSubscriptions += subscription
            subscriptions += subscription
            invalidateHandlers()
            if (closed.get() || scopeClosed.get()) subscription.close()
            return subscription
        }

        override fun <E : Event> callEvent(event: E): E = this@EventServiceImpl.callEvent(event)

        override fun close() {
            closeInternal()
            synchronized(scopes) { if (scopes[pluginId] === this) scopes.remove(pluginId) }
        }

        fun closeInternal() {
            if (!scopeClosed.compareAndSet(false, true)) return
            ownedSubscriptions.forEach { it.close() }
            ownedSubscriptions.clear()
        }

        fun remove(subscription: Subscription<out Event>) {
            ownedSubscriptions.remove(subscription)
        }

        private fun subscribeHandler(
            listener: Listener,
            handler: EventHandlerInspector.Handler,
        ): EventSubscription {
            @Suppress("UNCHECKED_CAST")
            val eventType = handler.eventType as Class<Event>
            return subscribe(
                eventType,
                handler.annotation.priority,
                handler.annotation.ignoreCancelled,
                Consumer { event -> handler.invoke(listener, event) },
            )
        }
    }

    private inner class Subscription<E : Event>(
        val scope: Scope,
        val eventType: Class<E>,
        val priority: Int,
        val ignoreCancelled: Boolean,
        private val listener: Consumer<E>,
        val order: Long,
    ) : EventSubscription {
        private val subscriptionClosed = AtomicBoolean(false)
        override val isClosed: Boolean get() = subscriptionClosed.get()

        fun invoke(event: Event) {
            listener.accept(eventType.cast(event))
        }

        override fun close() {
            if (!subscriptionClosed.compareAndSet(false, true)) return
            subscriptions.remove(this)
            invalidateHandlers()
            scope.remove(this)
        }
    }

    private class ListenerRegistration(
        override val listener: Listener,
        private val subscriptions: List<EventSubscription>,
    ) : EventListenerRegistration {
        private val registrationClosed = AtomicBoolean(false)
        override val handlerCount: Int get() = subscriptions.size
        override val isClosed: Boolean
            get() = registrationClosed.get() || subscriptions.all { it.isClosed }

        override fun close() {
            if (!registrationClosed.compareAndSet(false, true)) return
            subscriptions.forEach { it.close() }
        }
    }

}
