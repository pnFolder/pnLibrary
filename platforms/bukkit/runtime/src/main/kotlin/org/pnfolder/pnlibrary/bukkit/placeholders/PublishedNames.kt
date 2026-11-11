package org.pnfolder.pnlibrary.bukkit.placeholders

/** Finds which published name PlaceholderAPI's text (`war~score.Alex`, `tag_x`) belongs to. */
internal object PublishedNames {
    /** A found name with the values of its brace parts. */
    data class Found<H>(val handler: H, val parameters: Map<String, String>)

    /** The name that equals [params], then the most specific pattern that fits, otherwise the longest plain name that [params] equals or continues after `_`. */
    fun <H> find(handlers: Map<String, H>, params: String): Found<H>? {
        handlers.entries.firstOrNull { '{' !in it.key && params.equals(it.key, true) }?.let { return Found(it.value, emptyMap()) }
        val byPattern = handlers.entries
            .mapNotNull { (name, handler) -> ExternalNames.match(name, params)?.let { Triple(name, handler, it) } }
            .maxByOrNull { (name, _, _) -> ExternalNames.specificity(name) }
        if (byPattern != null) return Found(byPattern.second, byPattern.third)
        val match = handlers.entries.sortedByDescending { it.key.length }.firstOrNull {
            '{' !in it.key && (params.equals(it.key, true) || params.startsWith(it.key + "_", true))
        } ?: return null
        val suffix = params.removePrefix(match.key).removePrefix("_")
        return Found(match.value, if (suffix.isEmpty()) emptyMap() else mapOf("value" to suffix))
    }
}
