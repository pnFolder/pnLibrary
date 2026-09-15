package ru.privatenull.pnlibrary.api.plugin

import java.util.function.Consumer

/**
 * Global registry of plugins integrated with one pnLibrary runtime.
 *
 * Both the normalized [PluginId] and native owner identity must be unique while a context is live.
 * Registration is atomic: configured listeners and optional integrations either become available
 * together or are rolled back together.
 *
 * ```kotlin
 * val context = library.plugins.register(plugin) { builder ->
 *     builder.metrics(12345)
 *     builder.listener(MyListener())
 * }
 * context.lifecycle.enabled().show()
 * ```
 */
interface PluginRegistry : AutoCloseable {
    /** Registers [owner] using the ID exposed by its platform adapter. */
    fun register(owner: Any, configure: Consumer<PluginBuilder>): PluginContext

    /**
     * Registers one plugin and atomically creates all configured capabilities.
     *
     * @throws IllegalStateException when this registry is closed
     * @throws IllegalArgumentException when [id] or [owner] is already registered
     */
    fun register(owner: Any, id: PluginId, configure: Consumer<PluginBuilder>): PluginContext

    /** Convenience overload that validates and normalizes [id]. */
    fun register(owner: Any, id: String, configure: Consumer<PluginBuilder>): PluginContext =
        register(owner, PluginId.of(id), configure)

    /** Returns the live context for [id], or `null`. */
    fun get(id: PluginId): PluginContext?
    /** Normalizes [id] and returns its live context, or `null`. */
    fun get(id: String): PluginContext? = get(PluginId.of(id))
    /** Returns the live context for [id] or fails with a readable error. */
    fun require(id: PluginId): PluginContext =
        get(id) ?: error("Plugin $id is not registered in pnLibrary")
    /** Normalizes [id] and returns its live context or fails. */
    fun require(id: String): PluginContext = require(PluginId.of(id))

    /** Closes and removes a plugin context. */
    fun unregister(id: PluginId)
    /** Normalizes [id], then closes and removes its context when present. */
    fun unregister(id: String) = unregister(PluginId.of(id))

    /** Platform lifecycle hook; consumers should normally close their context. */
    fun unregisterOwner(owner: Any)

    /** Returns a stable snapshot of all current contexts. */
    fun registrations(): List<PluginContext>

    /** Closes every context and permanently closes this registry. */
    override fun close()
}
