package org.pnfolder.pnlibrary.api.actions

/**
 * Reads a requirement written as one line of text.
 *
 * | Text | Meaning |
 * |---|---|
 * | `%vault_eco_balance% >= 100` | number comparison with `==`, `!=`, `>=`, `<=`, `>`, `<` |
 * | `%rank% is vip` | text equal, ignoring case |
 * | `%player_name% contains Al` | text contains |
 * | `%code% matches [A-Z]{2}-\d+` | the whole text fits a regular expression |
 * | `permission shop.use` | the player has the permission |
 *
 * A `!` or `not` before the text turns it around.
 */
internal object RequirementExpression {
    private val symbol = Regex("""^(.+?)\s*(==|!=|>=|<=|>|<)\s*(.+)$""")
    private val word = Regex("""^(.+?)\s+(is|contains|matches)\s+(.+)$""", RegexOption.IGNORE_CASE)
    private val permission = Regex("""^permission\s+(\S+)$""", RegexOption.IGNORE_CASE)

    /**
     * Builds the condition written in [text].
     *
     * @throws IllegalArgumentException when [text] is none of the supported forms
     */
    fun parse(text: String): ActionCondition {
        var body = text.trim()
        var negated = false
        if (body.startsWith("!")) {
            negated = true
            body = body.substring(1).trim()
        } else if (body.startsWith("not ", ignoreCase = true)) {
            negated = true
            body = body.substring(4).trim()
        }
        val condition = permission.matchEntire(body)?.let { PermissionCondition(it.groupValues[1]) }
            ?: word.matchEntire(body)?.let { match -> wordCondition(match) }
            ?: symbol.matchEntire(body)?.let { match ->
                ValueCondition(
                    match.groupValues[1].trim(),
                    ConditionSerializer.comparators.getValue(match.groupValues[2]),
                    match.groupValues[3].trim(),
                )
            }
            ?: throw IllegalArgumentException(
                "Cannot read requirement '$text'. Write a comparison such as '%vault_eco_balance% >= 100', " +
                    "'%rank% is vip' or 'permission shop.use', or use 'type:'",
            )
        return if (negated) NotCondition(condition) else condition
    }

    private fun wordCondition(match: MatchResult): ActionCondition {
        val left = match.groupValues[1].trim()
        val right = match.groupValues[3].trim()
        return when (match.groupValues[2].lowercase()) {
            "is" -> ValueCondition(left, Comparison.EQUALS, right, ignoreCase = true)
            "contains" -> ValueCondition(left, Comparison.CONTAINS, right, ignoreCase = false)
            else -> RegexCondition(left, right)
        }
    }
}
