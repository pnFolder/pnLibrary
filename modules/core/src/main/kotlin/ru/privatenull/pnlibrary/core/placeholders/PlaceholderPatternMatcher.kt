package ru.privatenull.pnlibrary.core.placeholders

/**
 * Matches a concrete placeholder key against a parameterized key pattern.
 *
 * Parameters use braces, for example `player.stat.{name}` matches
 * `player.stat.kills` and returns `name=kills`. A parameter consumes exactly
 * one dot-delimited segment; literal portions of a pattern are matched
 * verbatim.
 */
internal object PlaceholderPatternMatcher {
    private val parameter = Regex("\\{([a-zA-Z0-9_-]+)}")

    /** Returns captured parameters, or `null` when [value] does not match [pattern]. */
    fun match(pattern: String, value: String): Map<String, String>? {
        val tokens = parameter.findAll(pattern).toList()
        if (tokens.isEmpty()) return null

        var cursor = 0
        val expression = buildString {
            append('^')
            tokens.forEach { token ->
                append(Regex.escape(pattern.substring(cursor, token.range.first)))
                append("([^.]+)")
                cursor = token.range.last + 1
            }
            append(Regex.escape(pattern.substring(cursor)))
            append('$')
        }
        val result = Regex(expression).matchEntire(value) ?: return null
        return tokens.mapIndexed { index, token ->
            token.groupValues[1] to result.groupValues[index + 1]
        }.toMap()
    }
}
