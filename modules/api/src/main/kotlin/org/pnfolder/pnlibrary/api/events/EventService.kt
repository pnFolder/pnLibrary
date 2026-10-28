package org.pnfolder.pnlibrary.api.events

import org.pnfolder.pnlibrary.api.plugin.ModuleId
import org.pnfolder.pnlibrary.api.plugin.PluginId
import java.util.concurrent.CompletionStage
import java.util.concurrent.Executor
import java.util.function.Consumer

/**
 * Process-wide platform-independent event bus.
 *
 * Use [scope] once per [PluginId] and retain the returned handle. Event
 * classes and listeners depend only on `pnlibrary-api`, so the same code runs on
 * Bukkit, BungeeCord, and Velocity. [callEvent] invokes matching listeners immediately on the
 * calling thread and returns only after dispatch has finished. This makes mutable and resulted
 * events predictable: their final state is available as soon as the method returns.
 * Scope mutation and shutdown are safe from arbitrary threads.
 */
interface EventService : AutoCloseable {
    /** Returns the existing plugin scope or creates it atomically. */
    fun scope(pluginId: PluginId): EventScope

    /**
     * Returns the scope of one module. [pluginId] is the unique key of the scope; [plugin] and [module] are the
     * names reported by [inspector] and [EventTrace] for everything registered through it.
     */
    fun scope(pluginId: PluginId, plugin: PluginId, module: ModuleId): EventScope

    /** Registers all annotated methods from [listener] under [pluginId]. */
    fun register(pluginId: PluginId, listener: Listener): EventListenerRegistration =
        scope(pluginId).register(listener)

    /** Registers one typed listener under [pluginId] using normal priority. */
    fun <E : Event> subscribe(
        pluginId: PluginId,
        eventType: Class<E>,
        listener: Consumer<E>,
    ): EventSubscription = scope(pluginId).subscribe(eventType, listener)

    /** Registers one typed listener under [pluginId] with complete dispatch options. */
    fun <E : Event> subscribe(
        pluginId: PluginId,
        eventType: Class<E>,
        priority: Int,
        ignoreDenied: Boolean,
        listener: Consumer<E>,
    ): EventSubscription = scope(pluginId).subscribe(eventType, priority, ignoreDenied, listener)

    /**
     * Invokes every matching listener synchronously and returns the same event instance.
     * Listener-written fields and results are final when this method returns.
     */
    fun <E : Event> callEvent(event: E): E

    /**
     * Dispatches [event] on the bus's own background thread ([DispatchMode.ASYNC]) and completes with the same
     * instance once every listener has run. Listeners must not touch the game world; use the overload with an
     * executor when they need to.
     */
    fun <E : Event> callEventAsync(event: E): CompletionStage<E>

    /**
     * Dispatches [event] on [executor] and completes with the same instance once every listener has run.
     * Listeners then run on the executor's thread, not the caller's, so pass a platform-appropriate executor
     * (for example one that hops to the server thread) when listeners touch the game world.
     */
    fun <E : Event> callEventAsync(event: E, executor: Executor): CompletionStage<E>

    /** Read-only view of listeners, call counters and traces of this bus. */
    val inspector: EventInspector

    /** Removes and closes the scope belonging to [pluginId]. */
    fun unregisterAll(pluginId: PluginId)

    /** Closes all scopes and prevents new subscriptions or publications. */
    override fun close()
}
