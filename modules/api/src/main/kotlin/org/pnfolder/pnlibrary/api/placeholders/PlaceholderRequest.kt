package org.pnfolder.pnlibrary.api.placeholders

import org.pnfolder.pnlibrary.api.plugin.PluginId
import java.util.UUID

/**
 * Immutable invocation data passed to a placeholder resolver.
 *
 * @property owner plugin that registered the placeholder
 * @property consumer plugin requesting the value
 * @property playerId player context, or `null` for a player-independent request
 * @property parameters named parameters extracted from the placeholder expression
 * @property values additional invocation values supplied by the caller
 */
data class PlaceholderRequest(
    val owner: PluginId,
    val consumer: PluginId,
    val playerId: UUID?,
    val parameters: Map<String, String>,
    val values: Map<String, Any?>,
) {
    /** Returns [playerId] or fails when this invocation has no player context. */
    fun requirePlayerId(): UUID = playerId ?: error("This placeholder requires a player")

    /** Returns a named parameter or fails when it was not supplied. */
    fun parameter(name: String): String = parameters[name] ?: error("Missing placeholder parameter: $name")
}
