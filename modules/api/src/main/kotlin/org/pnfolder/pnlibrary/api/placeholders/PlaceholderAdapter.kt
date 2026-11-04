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
    /** Replaces the tokens this platform owns in [text] (for PlaceholderAPI, every `%...%`); unknown ones stay. */
    fun render(playerId: UUID?, text: String): String = text
    /** Publishes [resolver] under the external name described by [publication]. */
    fun publish(owner: PluginId, key: String, resolver: PlaceholderResolver<Any>, publication: PlaceholderPublication): ExternalPlaceholderRegistration

    companion object {
        /** Request value (milliseconds, a `Long`) telling a published placeholder how long it may wait for a late answer. */
        const val WAIT_MILLIS_KEY: String = "pnlibrary.waitMillis"
    }
}
