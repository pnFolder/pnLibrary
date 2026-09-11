package ru.privatenull.pnlibrary.api.actions.impl

import java.time.Duration
import ru.privatenull.pnlibrary.api.actions.Action

class DelayImpl(
    var duration: Duration = Duration.ZERO,
    var actions: MutableList<Action> = mutableListOf(),
) : Action {
    override fun execute(context: Action.Context) {
        require(!duration.isNegative) { "Action delay must not be negative" }
        context.later(duration, actions)
    }
}
