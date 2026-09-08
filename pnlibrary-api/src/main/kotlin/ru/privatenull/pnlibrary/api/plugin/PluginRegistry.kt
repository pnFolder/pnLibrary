package ru.privatenull.pnlibrary.api.plugin

import java.util.function.Consumer

/** Global registry of plugins integrated with one pnLibrary runtime. */
interface PluginRegistry : AutoCloseable {
    /** Registers one plugin and atomically creates all configured capabilities. */
    fun register(owner: Any, id: PluginId, configure: Consumer<PluginBuilder>): PluginContext

    /** Convenience overload that validates and normalizes [id]. */
    fun register(owner: Any, id: String, configure: Consumer<PluginBuilder>): PluginContext =
        register(owner, PluginId.of(id), configure)

    fun get(id: PluginId): PluginContext?
    fun get(id: String): PluginContext? = get(PluginId.of(id))
    fun require(id: PluginId): PluginContext =
        get(id) ?: error("Plugin $id is not registered in pnLibrary")
    fun require(id: String): PluginContext = require(PluginId.of(id))

    /** Closes and removes a plugin context. */
    fun unregister(id: PluginId)
    fun unregister(id: String) = unregister(PluginId.of(id))

    /** Platform lifecycle hook; consumers should normally close their context. */
    fun unregisterOwner(owner: Any)

    /** Returns a stable snapshot of all current contexts. */
    fun registrations(): List<PluginContext>

    override fun close()
}
