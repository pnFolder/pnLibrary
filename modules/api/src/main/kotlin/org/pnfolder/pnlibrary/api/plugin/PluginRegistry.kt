package org.pnfolder.pnlibrary.api.plugin

import org.pnfolder.pnlibrary.api.libraries.LibraryState
import org.pnfolder.pnlibrary.api.libraries.LibraryUsage

/**
 * Registry of native platform plugins using one pnLibrary runtime.
 *
 * Registration, lookup, removal, and shutdown are serialized and safe from arbitrary threads.
 * [all] returns a detached immutable snapshot. Closing the registry closes plugins and their
 * modules in reverse ownership order.
 */
interface PluginRegistry : AutoCloseable {

    /** Registers [owner] or returns its existing registration. */
    fun register(owner: Any): PluginRegistration

    /** Returns [owner]'s registration, or `null` when absent. */
    fun get(owner: Any): PluginRegistration?

    /** Returns [owner]'s registration or fails when absent. */
    fun require(owner: Any): PluginRegistration =
        get(owner) ?: error("Platform plugin ${owner.javaClass.name} is not registered in pnLibrary")

    /** Returns the registration of the plugin with identifier [id], or `null` when no such plugin is registered. */
    fun getById(id: PluginId): PluginRegistration? = all().firstOrNull { it.id == id }

    /** Normalizes [id] and returns the matching registration, or `null`. */
    fun getById(id: String): PluginRegistration? = getById(PluginId.of(id))

    /** Returns the registration of the plugin with identifier [id], or fails naming the registered plugins. */
    fun requireById(id: PluginId): PluginRegistration =
        getById(id) ?: error("Plugin $id is not registered in pnLibrary; registered: ${all().joinToString { it.id.value }}")

    /** Normalizes [id] and returns the matching registration, or fails naming the registered plugins. */
    fun requireById(id: String): PluginRegistration = requireById(PluginId.of(id))

    /** Closes and removes [owner]'s registration when present. */
    fun unregister(owner: Any)

    /** Returns an immutable snapshot of registered platform plugins. */
    fun all(): List<PluginRegistration>

    /** Every module of every plugin registered in the library. */
    fun allModules(): List<ModuleContext> = all().flatMap { it.all() }

    /** Every library registered anywhere, and who registered it, so one used by many modules shows once per owner. */
    fun libraryUsage(): List<LibraryUsage> = all().flatMap { plugin ->
        plugin.libraries.statuses.filter { it.state != LibraryState.SKIPPED }.map { LibraryUsage(it.library, plugin.id, null, it) } +
            plugin.all().flatMap { module ->
                module.libraries.statuses.filter { it.state != LibraryState.SKIPPED }.map { LibraryUsage(it.library, plugin.id, module.id, it) }
            }
    }

    /** Closes all registrations in reverse ownership order. */
    override fun close()
}
