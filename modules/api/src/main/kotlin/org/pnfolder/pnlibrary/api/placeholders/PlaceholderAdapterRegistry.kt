package org.pnfolder.pnlibrary.api.placeholders

import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholder

/** The adapters of the outside placeholder systems that are connected. */
interface PlaceholderAdapterRegistry {
    /** Registers [adapter] and returns a handle that removes and closes it. */
    fun register(adapter: PlaceholderAdapter): AutoCloseable

    /** The adapter with case-insensitive [id], or `null`. */
    fun get(id: String): PlaceholderAdapter?

    /** The adapter or a failure when it is not connected. */
    fun require(id: String): PlaceholderAdapter = get(id) ?: error("Placeholder adapter $id is unavailable")

    /** A stable snapshot of the adapters ordered by identifier. */
    fun all(): List<PlaceholderAdapter>
}
