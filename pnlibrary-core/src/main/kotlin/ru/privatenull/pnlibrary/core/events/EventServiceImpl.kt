package ru.privatenull.pnlibrary.core.events

import ru.privatenull.pnlibrary.api.events.CancellablePnEvent
import ru.privatenull.pnlibrary.api.events.EventDispatchResult
import ru.privatenull.pnlibrary.api.events.EventScope
import ru.privatenull.pnlibrary.api.events.EventService
import ru.privatenull.pnlibrary.api.events.EventSubscription
import ru.privatenull.pnlibrary.api.events.PnEvent
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer

/** Thread-safe synchronous implementation of the platform-independent event bus. */
internal class EventServiceImpl(
    private val errorLogger: (Any, String, Throwable) -> Unit,
) : EventService {

    private val scopes = Collections.synchronizedMap(IdentityHashMap<Any, Scope>())
    private val subscriptions = CopyOnWriteArrayList<Subscription<out PnEvent>>()
    private val sequence = AtomicLong()
    private val closed = AtomicBoolean(false)

    override fun scope(owner: Any): EventScope {
        return synchronized(scopes) {
            check(!closed.get()) { "EventService is closed" }
            scopes.getOrPut(owner) { Scope(owner) }
        }
    }

    override fun publish(event: PnEvent): EventDispatchResult {
        check(!closed.get()) { "EventService is closed" }
        var delivered = 0
        var skipped = 0
        var failed = 0

        val matching = subscriptions.asSequence()
            .filter { !it.isClosed && it.eventType.isAssignableFrom(event.javaClass) }
            .sortedWith(compareBy<Subscription<out PnEvent>> { it.priority }.thenBy { it.order })
            .toList()

        matching.forEach { subscription ->
            if (subscription.isClosed) return@forEach
            if (subscription.ignoreCancelled && (event as? CancellablePnEvent)?.isCancelled == true) {
                skipped++
                return@forEach
            }
            try {
                subscription.invoke(event)
                delivered++
            } catch (error: Throwable) {
                failed++
                errorLogger(
                    subscription.scope.owner,
                    "[pnLibrary/events] Listener failed for ${event.javaClass.name}",
                    error,
                )
            }
        }

        return EventDispatchResult(
            delivered = delivered,
            skipped = skipped,
            failed = failed,
            cancelled = (event as? CancellablePnEvent)?.isCancelled == true,
        )
    }

    override fun close(owner: Any) {
        synchronized(scopes) { scopes.remove(owner) }?.closeInternal()
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        val current = synchronized(scopes) { scopes.values.toList().also { scopes.clear() } }
        current.forEach { it.closeInternal() }
        subscriptions.clear()
    }

    private inner class Scope(override val owner: Any) : EventScope {
        private val scopeClosed = AtomicBoolean(false)
        private val ownedSubscriptions = CopyOnWriteArrayList<Subscription<out PnEvent>>()
        override val isClosed: Boolean get() = scopeClosed.get()

        override fun <E : PnEvent> subscribe(
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

        override fun publish(event: PnEvent): EventDispatchResult = this@EventServiceImpl.publish(event)

        override fun close() {
            closeInternal()
            synchronized(scopes) { if (scopes[owner] === this) scopes.remove(owner) }
        }

        fun closeInternal() {
            if (!scopeClosed.compareAndSet(false, true)) return
            ownedSubscriptions.forEach { it.close() }
            ownedSubscriptions.clear()
        }

        fun remove(subscription: Subscription<out PnEvent>) {
            ownedSubscriptions.remove(subscription)
        }
    }

    private inner class Subscription<E : PnEvent>(
        val scope: Scope,
        val eventType: Class<E>,
        val priority: Int,
        val ignoreCancelled: Boolean,
        private val listener: Consumer<E>,
        val order: Long,
    ) : EventSubscription {
        private val subscriptionClosed = AtomicBoolean(false)
        override val isClosed: Boolean get() = subscriptionClosed.get()

        fun invoke(event: PnEvent) {
            listener.accept(eventType.cast(event))
        }

        override fun close() {
            if (!subscriptionClosed.compareAndSet(false, true)) return
            subscriptions.remove(this)
            scope.remove(this)
        }
    }
}
