package org.pnfolder.pnlibrary.core.placeholders

import org.pnfolder.pnlibrary.api.plugin.PluginId
import java.util.Locale

/** Resolves exact and patterned placeholder references across plugin namespaces. */
class PlaceholderEntryLookup(
    private val entries: Map<String, PlaceholderEntry<*>>,
    private val systemNamespace: PluginId,
) {
    /** The entry found, or null, with the values taken from the pattern's parameters. */
    data class Match(
        val entry: PlaceholderEntry<*>?,
        val parameters: Map<String, String>,
    )

    /** Finds the most specific entry visible under the namespace encoded in [reference]. */
    fun find(consumer: PluginId, reference: String): Match {
        val separator = reference.indexOf(':')
        val namespace = if (separator > 0) {
            PluginId.of(reference.substring(0, separator))
        } else {
            consumer
        }
        val key = if (separator > 0) reference.substring(separator + 1) else reference

        exact(namespace, key)?.let { return Match(it, emptyMap()) }
        if (separator < 0) {
            exact(systemNamespace, key)?.let { return Match(it, emptyMap()) }
        }

        return bestPattern(namespace, key)
    }

    private fun bestPattern(namespace: PluginId, key: String): Match {
        var best: PlaceholderEntry<*>? = null
        var bestParameters = emptyMap<String, String>()
        for (entry in entries.values) {
            val pattern = entry.pattern ?: continue
            if (entry.owner != namespace) continue
            val parameters = pattern.match(key) ?: continue
            if (best == null || pattern.specificity > best.pattern!!.specificity) {
                best = entry
                bestParameters = parameters
            }
        }
        return Match(best, bestParameters)
    }

    private fun exact(owner: PluginId, key: String): PlaceholderEntry<*>? =
        entries["${owner.value}:${key.lowercase(Locale.ROOT)}"]
}
