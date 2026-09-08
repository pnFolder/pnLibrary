package ru.privatenull.pnlibrary.core.events

import ru.privatenull.pnlibrary.api.events.Cancellable
import ru.privatenull.pnlibrary.api.events.Event
import ru.privatenull.pnlibrary.api.events.EventDispatchResult
import ru.privatenull.pnlibrary.api.events.EventHandler
import ru.privatenull.pnlibrary.api.events.EventMode
import ru.privatenull.pnlibrary.api.events.EventListenerRegistration
import ru.privatenull.pnlibrary.api.events.EventScope
import ru.privatenull.pnlibrary.api.events.EventService
import ru.privatenull.pnlibrary.api.events.EventSubscription
import ru.privatenull.pnlibrary.api.events.Listener
import ru.privatenull.pnlibrary.api.plugin.PluginId
import ru.privatenull.pnlibrary.api.tasks.TaskService
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer

/** Thread-safe, mode-aware implementation of the platform-independent event bus. */
internal class EventServiceImpl(
    tasks: TaskService,
    private val errorLogger: (PluginId, String, Throwable) -> Unit,
) : EventService {

    private val scopes = HashMap<PluginId, Scope>()
    private val subscriptions = CopyOnWriteArrayList<Subscription<out Event>>()
    private val sequence = AtomicLong()
    private val closed = AtomicBoolean(false)
    private val taskScope = tasks.scope(this)
    private val pendingCalls = ConcurrentHashMap.newKeySet<CompletableFuture<EventDispatchResult>>()

    override fun scope(pluginId: PluginId): EventScope {
        return synchronized(scopes) {
            check(!closed.get()) { "EventService is closed" }
            scopes.getOrPut(pluginId) { Scope(pluginId) }
        }
    }

    override fun publish(event: Event): CompletableFuture<EventDispatchResult> {
        check(!closed.get()) { "EventService is closed" }
        val future = CompletableFuture<EventDispatchResult>()
        pendingCalls += future
        future.whenComplete { _, _ -> pendingCalls.remove(future) }
        val dispatch = Runnable {
            try {
                future.complete(dispatchInline(event))
            } catch (error: Throwable) {
                future.completeExceptionally(error)
            }
        }
        try {
            when (event.mode) {
                EventMode.SYNC -> taskScope.global(dispatch)
                EventMode.ASYNC -> taskScope.async(dispatch)
            }
        } catch (error: Throwable) {
            future.completeExceptionally(error)
        }
        return future
    }

    private fun dispatchInline(event: Event): EventDispatchResult {
        check(!closed.get()) { "EventService is closed" }
        var delivered = 0
        var skipped = 0
        var failed = 0

        val matching = subscriptions.asSequence()
            .filter { !it.isClosed && it.eventType.isAssignableFrom(event.javaClass) }
            .sortedWith(compareBy<Subscription<out Event>> { it.priority }.thenBy { it.order })
            .toList()

        matching.forEach { subscription ->
            if (subscription.isClosed) return@forEach
            if (subscription.ignoreCancelled && (event as? Cancellable)?.isCancelled == true) {
                skipped++
                return@forEach
            }
            try {
                subscription.invoke(event)
                delivered++
            } catch (error: Throwable) {
                failed++
                errorLogger(
                    subscription.scope.pluginId,
                    "[pnLibrary/events] Listener failed for ${event.javaClass.name}",
                    error,
                )
            }
        }

        return EventDispatchResult(
            delivered = delivered,
            skipped = skipped,
            failed = failed,
            cancelled = (event as? Cancellable)?.isCancelled == true,
        )
    }

    override fun unregisterAll(pluginId: PluginId) {
        synchronized(scopes) { scopes.remove(pluginId) }?.closeInternal()
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        taskScope.close()
        pendingCalls.forEach { future ->
            future.completeExceptionally(CancellationException("EventService was closed before dispatch completed"))
        }
        pendingCalls.clear()
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
            val handlers = discoverHandlers(listener)
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

        override fun publish(event: Event): CompletableFuture<EventDispatchResult> =
            this@EventServiceImpl.publish(event)

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

        private fun subscribeHandler(listener: Listener, handler: HandlerMethod): EventSubscription {
            @Suppress("UNCHECKED_CAST")
            val eventType = handler.eventType as Class<Event>
            return subscribe(
                eventType,
                handler.annotation.priority,
                handler.annotation.ignoreCancelled,
                Consumer { event -> invokeHandler(handler.method, listener, event) },
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

    private data class HandlerMethod(
        val method: Method,
        val annotation: EventHandler,
        val eventType: Class<out Event>,
    )

    private fun discoverHandlers(listener: Listener): List<HandlerMethod> {
        val methods = linkedMapOf<String, Method>()
        var type: Class<*>? = listener.javaClass
        while (type != null && type != Any::class.java) {
            type.declaredMethods
                .asSequence()
                .filterNot { it.isBridge || it.isSynthetic }
                .forEach { method -> methods.putIfAbsent(methodKey(method), method) }
            type = type.superclass
        }

        return methods.values.mapNotNull { method ->
            val annotation = method.getAnnotation(EventHandler::class.java) ?: return@mapNotNull null
            require(!Modifier.isStatic(method.modifiers)) {
                "@EventHandler method must not be static: ${methodDescription(method)}"
            }
            require(!Modifier.isAbstract(method.modifiers)) {
                "@EventHandler method must not be abstract: ${methodDescription(method)}"
            }
            require(method.parameterCount == 1) {
                "@EventHandler method must have exactly one parameter: ${methodDescription(method)}"
            }
            require(Event::class.java.isAssignableFrom(method.parameterTypes[0])) {
                "@EventHandler parameter must implement Event: ${methodDescription(method)}"
            }
            require(method.returnType == Void.TYPE) {
                "@EventHandler method must return Unit or void: ${methodDescription(method)}"
            }
            method.isAccessible = true
            HandlerMethod(method, annotation, method.parameterTypes[0].asSubclass(Event::class.java))
        }.sortedWith(
            compareBy<HandlerMethod> { it.annotation.priority }
                .thenBy { it.method.name }
                .thenBy { it.eventType.name },
        )
    }

    private fun methodKey(method: Method): String {
        val visibilityOwner = if (Modifier.isPrivate(method.modifiers)) method.declaringClass.name else ""
        return "$visibilityOwner#${method.name}(${method.parameterTypes.joinToString(",") { it.name }})"
    }

    private fun methodDescription(method: Method): String =
        "${method.declaringClass.name}#${method.name}"

    private fun invokeHandler(method: Method, listener: Listener, event: Event) {
        try {
            method.invoke(listener, event)
        } catch (error: InvocationTargetException) {
            throw error.targetException
        }
    }
}
