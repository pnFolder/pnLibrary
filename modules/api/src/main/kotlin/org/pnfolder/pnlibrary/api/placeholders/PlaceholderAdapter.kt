package org.pnfolder.pnlibrary.api.placeholders

import org.pnfolder.pnlibrary.api.plugin.PluginId
import java.util.UUID

/** Bridge between pnLibrary placeholders and one external placeholder system. */
interface PlaceholderAdapter {
    /** Stable, case-insensitive adapter identifier. */
    val id: String
    /** Current adapter availability. */
    val state: PlaceholderAdapterState
    /** Feature set used to negotiate publication and resolution. */
    val capabilities: PlaceholderAdapterCapabilities
    /** Resolves a value owned by the external platform, or returns `null` when unknown. */
    fun resolve(playerId: UUID?, expression: String): Any? = null
    /** Publishes [resolver] under the external name described by [publication]. */
    fun publish(owner: PluginId, key: String, resolver: PlaceholderResolver<Any>, publication: PlaceholderPublication): ExternalPlaceholderRegistration
}
