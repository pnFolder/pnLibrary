package ru.privatenull.pnlibrary.api.actions

import java.time.Duration

/** Executes nested actions after [duration] without blocking the current thread. */
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
