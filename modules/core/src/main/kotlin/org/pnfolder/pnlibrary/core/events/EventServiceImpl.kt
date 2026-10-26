@file:Suppress("DEPRECATION")

package org.pnfolder.pnlibrary.core.events

import org.pnfolder.pnlibrary.api.events.Event
import org.pnfolder.pnlibrary.api.events.EventListenerRegistration
import org.pnfolder.pnlibrary.api.events.EventScope
import org.pnfolder.pnlibrary.api.events.EventService
import org.pnfolder.pnlibrary.api.events.EventSubscription
import org.pnfolder.pnlibrary.api.events.Listener
import org.pnfolder.pnlibrary.api.plugin.PluginId
import org.pnfolder.pnlibrary.common.lang.Failures
import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
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
     * Sorted listeners per event class. Adding or closing a subscription for type T drops only the cached
     * lists of classes that T matches, so churn on one event never slows the others. A list built while a
     * change was happening is returned for that call but not cached. Weak keys let an unloaded plugin's
     * event classes be collected when nothing listens to them.
     */
    private val generation = AtomicLong()
    private val cache = java.util.WeakHashMap<Class<*>, List<Subscription<out Event>>>()

    /** The most recently used lookup, readable without a lock: hot events repeat the same class. */
    private class Last(val type: Class<*>, val handlers: List<Subscription<out Event>>)
    @Volatile private var last: Last? = null

    /** Forgets cached lists affected by a subscription to [changed]; `null` forgets everything. */
    private fun invalidateHandlers(changed: Class<*>?) {
        synchronized(cache) {
            generation.incrementAndGet()
            last = null
            if (changed == null) cache.clear() else cache.keys.removeAll { changed.isAssignableFrom(it) }
        }
    }

    private fun handlersFor(type: Class<*>): List<Subscription<out Event>> {
        last?.let { if (it.type === type) return it.handlers }
        val before = generation.get()
        // Setting `last` inside the lock keeps it consistent with invalidation: a list read just before a
        // change can never be republished as the fast-path entry after the change cleared it.
        synchronized(cache) { cache[type]?.also { last = Last(type, it) } }?.let { return it }
        val built = subscriptions.asSequence()
            .filter { !it.isClosed && it.eventType.isAssignableFrom(type) }
            .sortedWith(compareBy<Subscription<out Event>> { it.priority }.thenBy { it.order })
            .toList()
        synchronized(cache) {
            if (generation.get() == before) {
                cache[type] = built
                last = Last(type, built)
            }
        }
        return built
    }

    private fun dispatchInline(event: Event) {
        val matching = handlersFor(event.javaClass)
        for (index in matching.indices) {
            val subscription = matching[index]
            if (subscription.isClosed) continue
            if (subscription.ignoreCancelled && !event.isAllowed) continue
            try {
                subscription.invoke(event)
            } catch (error: Throwable) {
                // A broken listener is logged and skipped; a dying JVM (out of memory, stack overflow) is not ours to hide.
                Failures.rethrowIfFatal(error)
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
        invalidateHandlers(null)
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
                registered.forEach { runCatchingNonFatal { it.close() } }
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
            invalidateHandlers(eventType)
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
            invalidateHandlers(eventType)
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
