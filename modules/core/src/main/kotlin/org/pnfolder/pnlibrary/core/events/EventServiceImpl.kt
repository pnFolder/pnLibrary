package org.pnfolder.pnlibrary.core.events

import org.pnfolder.pnlibrary.api.events.DispatchMode
import org.pnfolder.pnlibrary.api.events.Event
import org.pnfolder.pnlibrary.api.events.EventDay
import org.pnfolder.pnlibrary.api.events.EventInfo
import org.pnfolder.pnlibrary.api.events.EventInspector
import org.pnfolder.pnlibrary.api.events.EventListenerRegistration
import org.pnfolder.pnlibrary.api.events.EventScope
import org.pnfolder.pnlibrary.api.events.EventService
import org.pnfolder.pnlibrary.api.events.EventSubscription
import org.pnfolder.pnlibrary.api.events.EventTrace
import org.pnfolder.pnlibrary.api.events.InternalEventApi
import org.pnfolder.pnlibrary.api.events.Listener
import org.pnfolder.pnlibrary.api.events.ListenerInfo
import org.pnfolder.pnlibrary.api.events.ResultedEvent
import org.pnfolder.pnlibrary.api.plugin.ModuleId
import org.pnfolder.pnlibrary.api.plugin.PluginId
import org.pnfolder.pnlibrary.common.concurrent.ManagedWorker
import org.pnfolder.pnlibrary.common.lang.Failures
import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.LongAdder
import java.util.function.Consumer
import java.util.function.Supplier

/** Thread-safe, mode-aware implementation of the platform-independent event bus. */
internal class EventServiceImpl(
    private val history: EventHistory? = null,
    private val errorLogger: (PluginId, String, Throwable) -> Unit,
) : EventService {

    private val scopes = HashMap<PluginId, Scope>()
    private val subscriptions = CopyOnWriteArrayList<Subscription<out Event>>()
    private val sequence = AtomicLong()
    private val closed = AtomicBoolean(false)

    override fun scope(pluginId: PluginId): EventScope = scopeFor(pluginId, pluginId, null)

    override fun scope(pluginId: PluginId, plugin: PluginId, module: ModuleId): EventScope =
        scopeFor(pluginId, plugin, module)

    private fun scopeFor(pluginId: PluginId, plugin: PluginId, module: ModuleId?): EventScope {
        return synchronized(scopes) {
            check(!closed.get()) { "EventService is closed" }
            scopes.getOrPut(pluginId) { Scope(pluginId, plugin, module) }
        }
    }

    override fun <E : Event> callEvent(event: E): E = callAs(event, DispatchMode.SYNC)

    private val worker = ManagedWorker("pnLibrary-events", parallelism = ASYNC_THREADS)

    override fun <E : Event> callEventAsync(event: E): CompletionStage<E> = worker.submit { callAs(event, DispatchMode.ASYNC) }

    override fun <E : Event> callEventAsync(event: E, executor: Executor): CompletionStage<E> =
        CompletableFuture.supplyAsync(Supplier { callAs(event, DispatchMode.ASYNC) }, executor)

    private fun <E : Event> callAs(event: E, mode: DispatchMode): E {
        check(!closed.get()) { "EventService is closed" }
        try {
            dispatchInline(event, mode)
        } finally {
            count(event)
        }
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

    /** Traced event types and their capacity; empty almost always, so dispatch only reads one volatile field. */
    @Volatile private var traced: Map<Class<out Event>, Int> = emptyMap()
    private val recorded = java.util.ArrayDeque<EventTrace>()

    private class Counter(type: Class<*>) {
        val type = java.lang.ref.WeakReference(type)
        val calls = LongAdder()
        val denied = LongAdder()
    }

    private val counters = java.util.concurrent.ConcurrentHashMap<String, Counter>()

    private fun count(event: Event) {
        val name = event.javaClass.name
        val counter = counters[name] ?: counters.computeIfAbsent(name) { Counter(event.javaClass) }
        counter.calls.increment()
        if (!event.isAllowed) counter.denied.increment()
        history?.record(name, !event.isAllowed)
    }

    override val inspector: EventInspector = object : EventInspector {
        override fun listeners(eventType: Class<out Event>): List<ListenerInfo> =
            handlersFor(eventType).filterNot { it.isClosed }.map { it.info() }

        override fun listeners(): List<ListenerInfo> = subscriptions.asSequence()
            .filterNot { it.isClosed }
            .sortedWith(compareBy<Subscription<out Event>> { it.eventType.name }.thenBy { it.priority }.thenBy { it.order })
            .map { it.info() }
            .toList()

        override fun eventType(name: String): Class<out Event>? {
            val type = subscriptions.firstOrNull { !it.isClosed && it.eventType.name == name }?.eventType
                ?: counters[name]?.type?.get()
            return type?.asSubclass(Event::class.java)
        }

        override fun events(): List<EventInfo> {
            val types = LinkedHashMap<String, Class<*>?>()
            subscriptions.filterNot { it.isClosed }.forEach { types.putIfAbsent(it.eventType.name, it.eventType) }
            counters.forEach { (name, counter) -> if (types[name] == null) types[name] = counter.type.get() }
            return types.entries.map { (name, type) ->
                val counter = counters[name]
                val listeners = if (type == null) 0 else handlersFor(type).count { !it.isClosed }
                EventInfo(name, listeners, counter?.calls?.sum() ?: 0, counter?.denied?.sum() ?: 0)
            }.sortedBy { it.eventType }
        }

        override fun history(eventType: String?): List<EventDay> = history?.entries(eventType).orEmpty()

        override fun hourly(eventType: String?): List<org.pnfolder.pnlibrary.api.events.EventHour> =
            history?.hourly(eventType).orEmpty()

        override fun asyncQueued(): Int = worker.queued()

        override fun exportHistory(json: Boolean): java.nio.file.Path? = history?.export(json)

        override val historyDays: Int get() = history?.retention ?: 0

        override fun enableTracing(eventType: Class<out Event>, capacity: Int) {
            require(capacity in 1..1_000) { "Trace capacity must be between 1 and 1000" }
            synchronized(recorded) { traced = traced + (eventType to capacity) }
        }

        override fun disableTracing(eventType: Class<out Event>) {
            synchronized(recorded) { traced = traced - eventType }
        }

        override fun tracedEvents(): List<String> = traced.keys.map { it.name }.sorted()

        override fun traces(): List<EventTrace> = synchronized(recorded) { recorded.toList() }

        override fun clearTraces() {
            synchronized(recorded) { recorded.clear() }
        }
    }

    private fun tracingCapacity(type: Class<*>): Int? =
        traced.takeIf { it.isNotEmpty() }?.entries?.firstOrNull { it.key.isAssignableFrom(type) }?.value

    private fun resultText(event: Event): String? = (event as? ResultedEvent<*>)?.result?.toString()

    private fun record(event: Event, steps: List<EventTrace.Step>, capacity: Int, mode: DispatchMode) {
        val trace = EventTrace(event.eventName, event.isAllowed, resultText(event), steps, mode, Thread.currentThread().name, java.time.Instant.now())
        synchronized(recorded) {
            recorded.addLast(trace)
            while (recorded.size > capacity) recorded.removeFirst()
        }
    }

    @OptIn(InternalEventApi::class)
    private fun dispatchInline(event: Event, mode: DispatchMode) {
        event.bindDispatch(mode, Thread.currentThread())
        val matching = handlersFor(event.javaClass)
        val capacity = tracingCapacity(event.javaClass)
        val steps = if (capacity != null) ArrayList<EventTrace.Step>(matching.size) else null
        for (index in matching.indices) {
            val subscription = matching[index]
            if (subscription.isClosed) continue
            val before = if (steps != null) resultText(event) else null
            if (subscription.ignoreDenied && !event.isAllowed) {
                steps?.add(EventTrace.Step(subscription.scope.plugin, subscription.scope.module, subscription.priority, true, false, 0, before, before))
                continue
            }
            val started = System.nanoTime()
            var failed = false
            subscription.invocations.increment()
            try {
                subscription.invoke(event)
            } catch (error: Throwable) {
                failed = true
                subscription.failures.increment()
                // A broken listener is logged and skipped; a dying JVM (out of memory, stack overflow) is not ours to hide.
                Failures.rethrowIfFatal(error)
                // A logger that throws must not cost the remaining listeners their turn.
                runCatchingNonFatal {
                    errorLogger(
                        subscription.scope.plugin,
                        "[pnLibrary/events] Listener failed for ${event.javaClass.name}",
                        error,
                    )
                }
            } finally {
                val elapsed = System.nanoTime() - started
                subscription.totalNanos.add(elapsed)
                subscription.maxNanos.accumulateAndGet(elapsed, ::maxOf)
                steps?.add(
                    EventTrace.Step(
                        subscription.scope.plugin, subscription.scope.module, subscription.priority, false, failed,
                        elapsed, before, resultText(event),
                    ),
                )
            }
        }
        if (steps != null && capacity != null) record(event, steps, capacity, mode)
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
        worker.close()
        history?.close()
    }

    private inner class Scope(override val pluginId: PluginId, val plugin: PluginId, val module: ModuleId?) : EventScope {
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
            ignoreDenied: Boolean,
            listener: Consumer<E>,
        ): EventSubscription = subscribe(eventType, priority, ignoreDenied, listener, listener.javaClass.name)

        private fun <E : Event> subscribe(
            eventType: Class<E>,
            priority: Int,
            ignoreDenied: Boolean,
            listener: Consumer<E>,
            source: String,
        ): EventSubscription {
            check(!closed.get() && !scopeClosed.get()) { "EventScope is closed" }
            val subscription = Subscription(
                scope = this,
                eventType = eventType,
                priority = priority,
                ignoreDenied = ignoreDenied,
                listener = listener,
                order = sequence.getAndIncrement(),
                source = source,
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
                handler.annotation.ignoreDenied,
                Consumer { event -> handler.invoke(listener, event) },
                "${listener.javaClass.name}#${handler.method.name}",
            )
        }
    }

    private inner class Subscription<E : Event>(
        val scope: Scope,
        val eventType: Class<E>,
        val priority: Int,
        val ignoreDenied: Boolean,
        private val listener: Consumer<E>,
        val order: Long,
        val source: String,
    ) : EventSubscription {
        val invocations = LongAdder()
        val failures = LongAdder()
        val totalNanos = LongAdder()
        val maxNanos = AtomicLong()
        private val subscriptionClosed = AtomicBoolean(false)
        override val isClosed: Boolean get() = subscriptionClosed.get()

        fun info() = ListenerInfo(
            scope.plugin, scope.module, eventType.name, priority, ignoreDenied, source, invocations.sum(), failures.sum(),
            totalNanos.sum(), maxNanos.get(),
        )

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

    private companion object {
        const val ASYNC_THREADS = 2
    }
}
