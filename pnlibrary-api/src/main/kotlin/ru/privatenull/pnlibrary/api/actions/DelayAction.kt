package ru.privatenull.pnlibrary.api.actions

import java.time.Duration

/**
 * Schedules one conditional action branch without blocking the calling thread.
 *
 * Conditions are evaluated when the delay expires, not when [execute] is called. The
 * original [ActionContext] is retained until then, so custom objects placed in it must
 * remain valid for at least [duration]. An empty [conditions] list selects [actions].
 *
 * @property duration delay before condition evaluation; zero schedules immediately
 * @property conditions predicates that must all match after the delay
 * @property actions branch executed when every condition matches
 * @property otherwise branch executed when at least one condition does not match
 * @throws IllegalArgumentException when [duration] is negative
 */
data class DelayAction(
    val duration: Duration = Duration.ZERO,
    val conditions: List<ActionCondition> = emptyList(),
    val actions: List<Action> = emptyList(),
    val otherwise: List<Action> = emptyList(),
) : Action {
    init {
        require(!duration.isNegative) { "Action delay must not be negative" }
    }

    override fun execute(context: ActionContext) {
        context.tasks.later(duration, Runnable {
            val selected = if (conditions.all { it.matches(context) }) actions else otherwise
            context.execute(selected)
        })
    }
}
