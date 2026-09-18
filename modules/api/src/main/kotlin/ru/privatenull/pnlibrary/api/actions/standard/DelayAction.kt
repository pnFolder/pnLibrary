package ru.privatenull.pnlibrary.api.actions

import ru.privatenull.pnlibrary.api.config.ConfigKey
import java.time.Duration

/**
 * Schedules a list of actions without blocking the calling thread.
 *
 * The original [ActionContext] is retained until then, so custom objects placed in it
 * must remain valid for at least [duration]. Put a [ConditionalAction] in [thenActions]
 * when conditions must be evaluated after the delay.
 *
 * @property duration delay before condition evaluation; zero schedules immediately
 * @property thenActions actions executed after the delay
 * @throws IllegalArgumentException when [duration] is negative
 */
data class DelayAction(
    val duration: Duration = Duration.ZERO,
    @field:ConfigKey("then")
    val thenActions: List<Action> = emptyList(),
) : Action {
    init {
        require(!duration.isNegative) { "Action delay must not be negative" }
    }

    override fun execute(context: ActionContext) {
        context.tasks.later(duration, Runnable {
            context.execute(thenActions)
        })
    }
}
