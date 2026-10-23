package org.pnfolder.pnlibrary.core.expansions

import org.pnfolder.pnlibrary.api.expansions.Expansion
import org.pnfolder.pnlibrary.api.expansions.ExpansionManager
import org.pnfolder.pnlibrary.core.lifecycle.ResourceCleanup

/** Thread-safe expansion registry; [onFailure] receives every exception an expansion throws while starting or stopping. */
internal class ExpansionManagerImpl(
    private val onFailure: (Expansion, Exception) -> Unit,
) : ExpansionManager, AutoCloseable {
    private val lock = Any()
    private val expansions = linkedMapOf<String, Expansion>()
    private var closed = false

    override fun register(expansion: Expansion): AutoCloseable {
        synchronized(lock) {
            check(!closed) { "ExpansionManager is closed" }
            check(expansion.id !in expansions) { "Expansion '${expansion.id}' is already registered" }
            expansions[expansion.id] = expansion
        }
        guarded(expansion, expansion::enable)
        return AutoCloseable {
            if (synchronized(lock) { expansions.remove(expansion.id, expansion) }) guarded(expansion, expansion::disable)
        }
    }

    override fun get(id: String): Expansion? = synchronized(lock) { expansions[id] }

    override fun <T : Expansion> get(type: Class<T>): T? = all().filterIsInstance(type).firstOrNull()

    override fun all(): List<Expansion> = synchronized(lock) { expansions.values.toList() }

    override fun close() {
        val registered = synchronized(lock) {
            closed = true
            expansions.values.toList().also { expansions.clear() }
        }
        ResourceCleanup.closeAll(*registered.asReversed().map { expansion -> { expansion.close() } }.toTypedArray())
    }

    private fun guarded(expansion: Expansion, action: () -> Unit) {
        try {
            action()
        } catch (error: Exception) {
            onFailure(expansion, error)
        }
    }
}
