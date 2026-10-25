package org.pnfolder.pnlibrary.api.placeholders

/**
 * Features supported by one external adapter.
 *
 * @property players accepts online-player context
 * @property offlinePlayers accepts UUIDs for players that are not online
 * @property parameters preserves arguments from external placeholder expressions
 * @property components can exchange Adventure components without flattening to text
 * @property asynchronous can resolve without blocking the adapter's calling thread
 */
data class PlaceholderAdapterCapabilities(
    val players: Boolean = true,
    val offlinePlayers: Boolean = false,
    val parameters: Boolean = true,
    val components: Boolean = false,
    val asynchronous: Boolean = false,
)
