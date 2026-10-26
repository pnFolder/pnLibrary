package org.pnfolder.pnlibrary.core.plugin

import org.pnfolder.pnlibrary.api.expansions.Expansion
import org.pnfolder.pnlibrary.api.expansions.ExpansionManager
import org.pnfolder.pnlibrary.core.expansions.ModuleBinding
import org.pnfolder.pnlibrary.core.expansions.ModuleBoundExpansion
import org.pnfolder.pnlibrary.core.lifecycle.ResourceCleanup

/**
 * One module's hold on expansions: what it registers ends with it, and it gets its own view of each module-bound
 * library expansion. Lookups read the plugins' registry; the library's own expansions are reached only through [view].
 */
internal class ModuleExpansions(
    private val shared: ExpansionManager,
    private val library: ExpansionManager,
) : ExpansionManager by shared, AutoCloseable {
    private val lock = Any()
    private val registered = linkedMapOf<Expansion, AutoCloseable>()
    private val views = hashMapOf<Class<*>, Any>()
    private val owned = mutableListOf<AutoCloseable>()
    private var closed = false

    private val binding = object : ModuleBinding {
        override fun <R : AutoCloseable> own(acquire: () -> R): R = synchronized(lock) {
            check(!closed) { "Module is closed" }
            acquire().also { owned += it }
        }
    }

    /** The view of [type] for this module, bound on first use. */
    fun <V : Any, E : ModuleBoundExpansion<V>> view(type: Class<E>): V = synchronized(lock) {
        check(!closed) { "Module is closed" }
        @Suppress("UNCHECKED_CAST")
        views.getOrPut(type) {
            (library.get(type) ?: throw UnsupportedOperationException(
                "${type.simpleName.removeSuffix("Expansion")} is not available on this platform",
            )).bind(binding)
        } as V
    }

    override fun register(expansion: Expansion): AutoCloseable = synchronized(lock) {
        check(!closed) { "Module is closed" }
        registered[expansion] = shared.register(expansion)
        AutoCloseable { synchronized(lock) { registered.remove(expansion) }?.close() }
    }

    override fun close() {
        val resources = synchronized(lock) {
            closed = true
            views.clear()
            (registered.values.toList() + owned).also { registered.clear(); owned.clear() }
        }
        ResourceCleanup.closeAll(*resources.asReversed().map { resource -> { resource.close() } }.toTypedArray())
    }
}
