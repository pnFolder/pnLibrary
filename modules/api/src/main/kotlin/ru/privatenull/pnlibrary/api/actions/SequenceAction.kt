package ru.privatenull.pnlibrary.api.actions

import ru.privatenull.pnlibrary.api.config.ConfigKey

/**
 * Executes a group of actions in declaration order.
 *
 * A sequence is the explicit composition primitive for configuration files. It
 * does not create a new execution context and it does not schedule work by
 * itself; nested [DelayAction] values are responsible for asynchronous steps.
 *
 * @property steps nested actions executed from first to last
 */
data class SequenceAction(
    @field:ConfigKey("steps")
    val steps: List<Action> = emptyList(),
) : Action {
    override fun execute(context: ActionContext) {
        context.execute(steps)
    }
}
