package org.pnfolder.pnlibrary.api.placeholders

import org.pnfolder.pnlibrary.api.plugin.PluginId

/**
 * Live, plugin-owned placeholder registration.
 *
 * Closing a registration disables resolution, clears its cache, and closes all external
 * publications. [close] is safe to call repeatedly.
 */
interface PlaceholderRegistration<T : Any> : AutoCloseable {

    /** Plugin that owns the resolver and its lifecycle. */
    val owner: PluginId

    /** Validated key and runtime result type. */
    val key: PlaceholderKey<T>

    /** Whether requests are currently delegated to the resolver. */
    val isEnabled: Boolean

    /** Whether this registration has been permanently removed. */
    val isClosed: Boolean get() = false

    /** External publications, including handles waiting for unavailable adapters. */
    val publications: List<ExternalPlaceholderRegistration>

    /** Enables resolver invocation for subsequent requests. */
    fun enable()

    /** Makes subsequent requests return the fallback without invoking the resolver. */
    fun disable()

    /** Removes every cached result belonging to this registration. */
    fun invalidateCache()
}
