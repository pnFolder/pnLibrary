package ru.privatenull.pnlibrary.api.plugin

import java.util.Collections

/**
 * Registry of native platform plugins using one pnLibrary runtime.
 *
 * Registration, lookup, removal, and shutdown are serialized and safe from arbitrary threads.
 * [all] returns a detached immutable snapshot. Closing the registry closes plugins and their
 * modules in reverse ownership order.
 */
interface PluginRegistry : AutoCloseable {
    fun register(owner: Any): PluginRegistration
    fun get(owner: Any): PluginRegistration?
    fun require(owner: Any): PluginRegistration =
        get(owner) ?: error("Platform plugin ${owner.javaClass.name} is not registered in pnLibrary")
    fun unregister(owner: Any)
    /** Returns an immutable snapshot of registered platform plugins. */
    @Suppress("DEPRECATION")
    fun all(): List<PluginRegistration> = Collections.unmodifiableList(ArrayList(registrations()))
    /** Compatibility alias for [all]. */
    @Deprecated("Use all()", ReplaceWith("all()"))
    fun registrations(): List<PluginRegistration>
    override fun close()
}
