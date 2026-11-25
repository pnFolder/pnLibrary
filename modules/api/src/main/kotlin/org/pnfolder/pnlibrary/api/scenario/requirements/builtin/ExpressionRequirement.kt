package org.pnfolder.pnlibrary.api.scenario.requirements.builtin

import org.pnfolder.pnlibrary.api.scenario.expression.Expression
import org.pnfolder.pnlibrary.api.scenario.expression.ExpressionException
import org.pnfolder.pnlibrary.api.scenario.requirements.Requirement
import org.pnfolder.pnlibrary.api.scenario.requirements.RequirementContext
import org.pnfolder.pnlibrary.api.scenario.requirements.Verdict

/**
 * Met when a calculation comes out true. This is the way to write anything the other types cannot: maths, the
 * player's level, placeholders, and any mix of them. See [Expression] for the whole language.
 *
 * ```yaml
 * type: expression
 * expression: "level() >= 10 && (permission('shop.vip') || {shop:discount} > 20)"
 * ```
 *
 * A mistake in the text is reported when the configuration loads. A calculation that fails at run time
 * (for example a text where a number is needed) is not met, and the reason says why.
 *
 * @property expression the calculation
 * @throws org.pnfolder.pnlibrary.api.scenario.expression.ExpressionException when the text cannot be parsed
 */
data class ExpressionRequirement(val expression: String = "true") : Requirement {
    private val parsed = Expression.parse(expression)

    override fun test(context: RequirementContext): Verdict = try {
        Verdict.of(parsed.test(context), "'$expression' is false")
    } catch (error: ExpressionException) {
        context.logger.warning("Expression '$expression' failed: ${error.message}")
        Verdict.Unmet("'$expression' could not be calculated: ${error.message}")
    }
}
