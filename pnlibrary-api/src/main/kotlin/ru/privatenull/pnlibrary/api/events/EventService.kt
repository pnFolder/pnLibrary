package ru.privatenull.pnlibrary.api.events

import ru.privatenull.pnlibrary.api.plugin.PluginId
import java.util.concurrent.CompletionStage
import java.util.function.Consumer

/**
 * Process-wide platform-independent event bus.
 *
 * Use [scope] once per [PluginId] and retain the returned handle. Event
 * classes and listeners depend only on `pnlibrary-api`, so the same code runs on
 * Bukkit, BungeeCord, and Velocity. Synchronous and asynchronous event modes
 * are explicit and cannot be invoked through the wrong publish method.
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

    /** Publishes a non-asynchronous [event] inline on the calling thread. */
    fun publish(event: Event): EventDispatchResult

    /** Publishes an asynchronous [event] on the event executor. */
    fun publishAsync(event: Event): CompletionStage<EventDispatchResult>

    /** Removes and closes the scope belonging to [pluginId]. */
    fun unregisterAll(pluginId: PluginId)

    /** Closes all scopes and prevents new subscriptions or publications. */
    override fun close()
}
