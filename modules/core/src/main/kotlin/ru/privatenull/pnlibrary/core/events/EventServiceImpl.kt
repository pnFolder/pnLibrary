@file:Suppress("DEPRECATION")

package ru.privatenull.pnlibrary.core.events

import ru.privatenull.pnlibrary.api.events.Cancellable
import ru.privatenull.pnlibrary.api.events.Event
import ru.privatenull.pnlibrary.api.events.EventListenerRegistration
import ru.privatenull.pnlibrary.api.events.EventScope
import ru.privatenull.pnlibrary.api.events.EventService
import ru.privatenull.pnlibrary.api.events.EventSubscription
import ru.privatenull.pnlibrary.api.events.Listener
import ru.privatenull.pnlibrary.api.plugin.PluginId
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

    private fun dispatchInline(event: Event) {
        check(!closed.get()) { "EventService is closed" }
        val matching = subscriptions.asSequence()
            .filter { !it.isClosed && it.eventType.isAssignableFrom(event.javaClass) }
            .sortedWith(compareBy<Subscription<out Event>> { it.priority }.thenBy { it.order })
            .toList()

        matching.forEach { subscription ->
            if (subscription.isClosed) return@forEach
            if (subscription.ignoreCancelled && (event as? Cancellable)?.isCancelled == true) {
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
