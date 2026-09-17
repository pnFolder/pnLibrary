package ru.privatenull.pnlibrary.core.placeholders

/** Parsed single-bracket placeholder backed by values local to an action context. */
internal data class LocalPlaceholderExpression(
    val reference: String,
    val formatters: List<String>,
) {
    companion object {
        /** Parses `[reference|formatter:argument]`, or returns `null` for malformed input. */
        fun parse(token: String): LocalPlaceholderExpression? {
            if (token.length < 3 || token.first() != '[' || token.last() != ']') return null
            val body = token.substring(1, token.length - 1)
            if ('[' in body || ']' in body) return null

            val parts = body.split('|').map(String::trim)
            val reference = parts.firstOrNull().orEmpty()
            if (reference.isEmpty()) return null

            return LocalPlaceholderExpression(
                reference = reference,
                formatters = parts.drop(1).filter(String::isNotEmpty),
            )
        }
    }
}
