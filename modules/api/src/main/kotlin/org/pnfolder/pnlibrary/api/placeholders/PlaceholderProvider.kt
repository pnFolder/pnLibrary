package org.pnfolder.pnlibrary.api.placeholders

import org.pnfolder.pnlibrary.api.plugin.PluginId
import java.util.UUID
import java.util.concurrent.CompletionStage
import java.util.function.Consumer

/** Consumer view restricted to placeholders owned by one plugin. */
interface PlaceholderProvider {
    /** Plugin whose keys are addressed by this provider. */
    val pluginId: PluginId
    /** Resolves [key] as the current service owner, applying its access policy. */
    fun resolve(key: String, playerId: UUID? = null): CompletionStage<Any?>
    /** Returns the target plugin's keys visible to the current consumer. */
    fun keys(): Set<String>
}
