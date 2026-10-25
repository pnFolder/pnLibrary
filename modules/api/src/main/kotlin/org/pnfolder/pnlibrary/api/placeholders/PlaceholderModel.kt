package org.pnfolder.pnlibrary.api.placeholders

import java.util.concurrent.CompletionStage

/** Resolves one placeholder synchronously; `null` selects its configured fallback. */
fun interface PlaceholderResolver<T : Any> {
    /** Produces a value for [request], or `null` when no value is available. */
    fun resolve(request: PlaceholderRequest): T?
}

/** Resolves one placeholder asynchronously; a null result selects its fallback. */
fun interface AsyncPlaceholderResolver<T : Any> {
    /** Starts resolution and returns its eventual value. */
    fun resolve(request: PlaceholderRequest): CompletionStage<T?>
}

/** Handle tracking one publication in an external placeholder system. */
interface ExternalPlaceholderRegistration : AutoCloseable {
    /** Current publication state, including unavailable and failed attempts. */
    val state: PlaceholderAdapterState
    /** Whether this publication has been permanently closed. */
    val isClosed: Boolean get() = state == PlaceholderAdapterState.CLOSED
}

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
