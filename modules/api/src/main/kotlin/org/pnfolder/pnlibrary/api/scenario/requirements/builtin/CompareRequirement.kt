package org.pnfolder.pnlibrary.api.scenario.requirements.builtin

import org.pnfolder.pnlibrary.api.scenario.requirements.Requirement
import org.pnfolder.pnlibrary.api.scenario.requirements.RequirementContext
import org.pnfolder.pnlibrary.api.scenario.requirements.Verdict

/**
 * Compares two values. Each side is text with placeholders, so it can be a literal, a context value,
 * a pnLibrary placeholder or an external one.
 *
 * ```yaml
 * type: compare
 * left: '%vault_eco_balance%'
 * operator: greater-or-equal
 * right: 100
 * ```
 *
 * @property left first side
 * @property operator relation between the sides
 * @property right second side
 * @property ignoreCase whether text is compared without case sensitivity
 */
data class CompareRequirement(
    val left: String = "",
    val operator: CompareOperator = CompareOperator.EQUALS,
    val right: String = "",
    val ignoreCase: Boolean = true,
) : Requirement {
    override fun test(context: RequirementContext): Verdict {
        val a = context.text(left).trim()
        val b = context.text(right).trim()
        val x = a.toBigDecimalOrNull()
        val y = b.toBigDecimalOrNull()
        if (operator.numeric && (x == null || y == null)) {
            return Verdict.Unmet("'${if (x == null) a else b}' is not a number")
        }
        val met = when (operator) {
            CompareOperator.EQUALS -> if (x != null && y != null) x.compareTo(y) == 0 else a.equals(b, ignoreCase)
            CompareOperator.NOT_EQUALS -> if (x != null && y != null) x.compareTo(y) != 0 else !a.equals(b, ignoreCase)
            CompareOperator.GREATER -> x!! > y!!
            CompareOperator.GREATER_OR_EQUAL -> x!! >= y!!
            CompareOperator.LESS -> x!! < y!!
            CompareOperator.LESS_OR_EQUAL -> x!! <= y!!
            CompareOperator.CONTAINS -> a.contains(b, ignoreCase)
            CompareOperator.STARTS_WITH -> a.startsWith(b, ignoreCase)
            CompareOperator.ENDS_WITH -> a.endsWith(b, ignoreCase)
            CompareOperator.MATCHES -> matches(a, b, context)
        }
        return Verdict.of(met, "$a is not ${operator.name.lowercase().replace('_', ' ')} $b")
    }

    private fun matches(text: String, pattern: String, context: RequirementContext): Boolean = try {
        Regex(pattern, if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()).matches(text)
    } catch (error: IllegalArgumentException) {
        context.logger.warning("Invalid regular expression '$pattern': ${error.message}")
        false
    }
}
