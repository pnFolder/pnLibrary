package org.pnfolder.pnlibrary.api.expansions

import org.jetbrains.annotations.ApiStatus
import org.pnfolder.pnlibrary.api.registry.Registry

/**
 * Registry that owns the lifecycle of [Expansion]s: it enables them on registration and disables
 * them in reverse order when the library stops.
 *
 * A failing expansion is reported to the log and left [ExpansionState.FAILED]; it never prevents the
 * others from starting or stopping. Implementations are safe for concurrent use.
 *
 * Example:
 * ```kotlin
 * val handle = library.expansions.register(WeatherExpansion())
 * val weather = library.expansions.get(WeatherExpansion::class.java)
 * handle.close() // disables and unregisters
 * ```
 */
@ApiStatus.NonExtendable
interface ExpansionManager {
    /**
     * Registers and enables [expansion].
     *
     * @return a handle that unregisters the expansion and disables it
     * @throws IllegalStateException when an expansion with the same [Expansion.id] is registered
     */
    fun register(expansion: Expansion): AutoCloseable

    /** Returns the registered expansion with [id], or `null`. */
    fun get(id: String): Expansion?

    /** Returns the first registered expansion that is an instance of [type], or `null`. */
    fun <T : Expansion> get(type: Class<T>): T?

    /** Returns an immutable snapshot of the registered expansions in registration order. */
    fun all(): List<Expansion>

    /** Returns the registered expansion called [id], or fails naming the registered ones. */
    fun require(id: String): Expansion =
        get(id) ?: error("Expansion '$id' is not registered; registered: ${all().joinToString { it.id }}")

    /** The same expansions as [all], as a [Registry]: found by id, owned by the library. */
    fun registry(): Registry<Expansion>
}
