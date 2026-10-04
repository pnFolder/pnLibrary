package ru.privatenull.pnlibrary.core.placeholders

import ru.privatenull.pnlibrary.api.plugin.PluginId
import java.util.Locale

/** Resolves exact and patterned placeholder references across plugin namespaces. */
internal class PlaceholderEntryLookup(
    private val entries: Map<String, PlaceholderEntry<*>>,
    private val systemNamespace: PluginId,
) {
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

        return entries.values
            .asSequence()
            .filter { entry -> entry.owner == namespace }
            .mapNotNull { entry ->
                PlaceholderPatternMatcher.match(entry.key.value, key)
                    ?.let { parameters -> Match(entry, parameters) }
            }
            .maxByOrNull { match -> match.entry!!.key.value.length }
            ?: Match(null, emptyMap())
    }

    private fun exact(owner: PluginId, key: String): PlaceholderEntry<*>? =
        entries["${owner.value}:${key.lowercase(Locale.ROOT)}"]
}
