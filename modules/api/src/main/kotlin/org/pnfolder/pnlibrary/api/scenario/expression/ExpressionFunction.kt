package org.pnfolder.pnlibrary.api.scenario.expression

import org.pnfolder.pnlibrary.api.scenario.requirements.RequirementContext

/**
 * A function that expressions may call, such as `min(a, b)` or one a plugin adds with `ActionService.defineFunction`.
 *
 * ```kotlin
 * module.actions.defineFunction("kills") { ctx, _ -> stats.kills(ctx.player.uniqueId) }
 * // later, in a configuration:  when: { type: expression, expression: 'kills() >= 100' }
 * ```
 */
fun interface ExpressionFunction {
    /** Computes the result from [arguments]: numbers as `Double`, booleans, or text. */
    fun call(context: RequirementContext, arguments: List<Any?>): Any?
}
