package org.pnfolder.pnlibrary.api.scenario.actions.builtin

import org.pnfolder.pnlibrary.api.scenario.actions.Action
import org.pnfolder.pnlibrary.api.scenario.actions.ActionContext
import org.pnfolder.pnlibrary.api.scenario.actions.ActionResult
import org.pnfolder.pnlibrary.api.scenario.expression.Expression

/**
 * Stores a value for the steps after it, readable as `[name]`. Give either a [value] (text with placeholders) or
 * an [expression] (a calculation, see [Expression]).
 *
 * ```yaml
 * - { type: set, name: price, expression: "round(base * (1 - {shop:discount} / 100), 2)" }
 * - { type: message, messages: ['Price: [price]'] }
 * ```
 *
 * @property name the value's name
 * @property value text to store, with placeholders filled in
 * @property expression calculation to store; wins over [value]
 * @throws org.pnfolder.pnlibrary.api.scenario.expression.ExpressionException when [expression] cannot be parsed
 */
data class SetAction(
    val name: String = "",
    val value: String = "",
    val expression: String? = null,
) : Action {
    private val parsed = expression?.let(Expression::parse)

    override fun run(context: ActionContext): ActionResult {
        if (name.isBlank()) return ActionResult.Failed("set.no-name", "A set action needs a name")
        val result = parsed?.evaluate(context) ?: context.format(value)
        context.setValue(name, result)
        return ActionResult.Done
    }
}
