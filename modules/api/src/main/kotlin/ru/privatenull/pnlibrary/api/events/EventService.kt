package ru.privatenull.pnlibrary.api.events

import ru.privatenull.pnlibrary.api.plugin.PluginId
import java.util.function.Consumer

/**
 * Process-wide platform-independent event bus.
 *
 * Use [scope] once per [PluginId] and retain the returned handle. Event
 * classes and listeners depend only on `pnlibrary-api`, so the same code runs on
 * Bukkit, BungeeCord, and Velocity. [callEvent] invokes matching listeners immediately on the
 * calling thread and returns only after dispatch has finished. This makes mutable and cancellable
 * events predictable: their final state is available as soon as the method returns.
 * Scope mutation and shutdown are safe from arbitrary threads.
 */
interface EventService : AutoCloseable {
    /** Returns the existing plugin scope or creates it atomically. */
    fun scope(pluginId: PluginId): EventScope

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
        ignoreCancelled: Boolean,
        listener: Consumer<E>,
    ): EventSubscription = scope(pluginId).subscribe(eventType, priority, ignoreCancelled, listener)

    /**
     * Invokes every matching listener synchronously and returns the same event instance.
     * Listener-written fields and cancellation state are final when this method returns.
     */
    fun <E : Event> callEvent(event: E): E

    /** Removes and closes the scope belonging to [pluginId]. */
    fun unregisterAll(pluginId: PluginId)

    /** Closes all scopes and prevents new subscriptions or publications. */
    override fun close()
}
