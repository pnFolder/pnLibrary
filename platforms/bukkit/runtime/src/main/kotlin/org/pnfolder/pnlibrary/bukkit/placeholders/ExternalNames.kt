package org.pnfolder.pnlibrary.bukkit.placeholders

/**
 * Matches the text PlaceholderAPI hands over (`war~score.Alex.kills`) against a published name that may have
 * parts in braces (`war~score.{who}.kills`). A brace part takes exactly one dot-separated part of the text.
 */
internal object ExternalNames {
    /** The values of the brace parts, or `null` when [text] does not fit [pattern]. */
    fun match(pattern: String, text: String): Map<String, String>? {
        if ('{' !in pattern) return null
        val wanted = pattern.split('.')
        val given = text.split('.')
        if (wanted.size != given.size) return null
        val values = linkedMapOf<String, String>()
        wanted.zip(given).forEach { (want, have) ->
            if (isParameter(want)) {
                if (have.isEmpty()) return null
                values[want.substring(1, want.length - 1)] = have
            } else if (!want.equals(have, ignoreCase = true)) {
                return null
            }
        }
        return values
    }

    /** How much of [pattern] is fixed text; the pattern with more of it is the more specific one. */
    fun specificity(pattern: String): Int = pattern.split('.').filterNot(::isParameter).sumOf { it.length }

    private fun isParameter(part: String) = part.length > 2 && part.startsWith("{") && part.endsWith("}")
}
