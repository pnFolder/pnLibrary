@file:Suppress("DEPRECATION")

package org.pnfolder.pnlibrary.api.actions.builtin

import org.pnfolder.pnlibrary.api.actions.Action
import org.pnfolder.pnlibrary.api.actions.ActionContext
import org.pnfolder.pnlibrary.api.config.ConfigKey
import org.pnfolder.pnlibrary.api.tasks.TaskExecution
import org.pnfolder.pnlibrary.api.tasks.TaskSpec
import java.time.Duration

/**
 * Waits [duration], then runs [thenActions] (written under `do`), without blocking the calling thread.
 *
 * Without actions of its own (`[wait 2s]`, or `type: delay` with no `do`) it pauses the rest of the list
 * instead. The original [ActionContext] is retained until then, so custom objects placed in it must remain
 * valid for at least [duration]. Put an [IfAction] in [thenActions] when a check must run after the delay.
 *
 * @property duration how long to wait; zero schedules immediately
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
        context.tasks.schedule(TaskSpec.builder()
            .execution(TaskExecution.global())
            .delay(duration)
            .action { context.execute(thenActions) }
            .build())
    }
}
