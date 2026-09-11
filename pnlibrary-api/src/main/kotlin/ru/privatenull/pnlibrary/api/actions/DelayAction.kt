package ru.privatenull.pnlibrary.api.actions

import java.time.Duration
import ru.privatenull.pnlibrary.api.config.ConfigType

/** Executes nested actions after [duration] without blocking the current thread. */
@ConfigType("delay")
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
