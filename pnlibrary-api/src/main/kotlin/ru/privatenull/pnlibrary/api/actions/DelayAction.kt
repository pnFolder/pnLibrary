package ru.privatenull.pnlibrary.api.actions

import java.time.Duration

/** Executes nested actions after [duration] without blocking the current thread. */
data class DelayAction(
    val duration: Duration = Duration.ZERO,
    val actions: List<Action> = emptyList(),
) : Action {
    init {
        require(!duration.isNegative) { "Action delay must not be negative" }
    }

    override fun execute(context: Action.Context) {
        context.later(duration, actions)
    }
}
