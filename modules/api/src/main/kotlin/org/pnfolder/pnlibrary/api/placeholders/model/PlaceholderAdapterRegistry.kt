package org.pnfolder.pnlibrary.api.placeholders.model

import org.pnfolder.pnlibrary.api.placeholders.adapter.PlaceholderAdapter

/** Runtime registry of available external placeholder adapters. */
interface PlaceholderAdapterRegistry {
    /** Registers [adapter] and returns a handle that removes and closes it. */
    fun register(adapter: PlaceholderAdapter): AutoCloseable
    /** Returns the adapter with case-insensitive [id], or `null`. */
    fun get(id: String): PlaceholderAdapter?
    /** Returns an adapter or fails when it is unavailable. */
    fun require(id: String): PlaceholderAdapter = get(id) ?: error("Placeholder adapter $id is unavailable")
    /** Returns a stable snapshot of adapters ordered by identifier. */
    fun all(): List<PlaceholderAdapter>
}
