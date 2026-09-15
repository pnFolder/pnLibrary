package ru.privatenull.pnlibrary.api.actions

/** Defines how [ConditionalAction.conditions] are combined. */
enum class ConditionMode {
    /** Every condition must match; an empty list matches. */
    ALL,
    /** At least one condition must match; an empty list does not match. */
    ANY,
}

/**
 * Evaluates conditions and immediately executes one branch in declaration order.
 *
 * [ConditionMode.ALL] matches an empty condition list, while [ConditionMode.ANY] does
 * not. Branch actions receive the original [ActionContext] and execute synchronously.
 *
 * @property conditions predicates evaluated according to [mode]
 * @property mode operation used to combine condition results
 * @property actions branch executed when the combined result is true
 * @property otherwise branch executed when the combined result is false
 */
data class ConditionalAction(
    val conditions: List<ActionCondition> = emptyList(),
    val mode: ConditionMode = ConditionMode.ALL,
    val actions: List<Action> = emptyList(),
    val otherwise: List<Action> = emptyList(),
) : Action {
    override fun execute(context: ActionContext) {
        val matches = when (mode) {
            ConditionMode.ALL -> conditions.all { it.matches(context) }
            ConditionMode.ANY -> conditions.any { it.matches(context) }
        }
        context.execute(if (matches) actions else otherwise)
    }
}
