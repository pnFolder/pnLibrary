package org.pnfolder.pnlibrary.api.actions

import java.time.Duration

/**
 * Pauses the rest of the list it is written in.
 *
 * Every action after the pause runs once [duration] has passed. Actions before it, and the lists that
 * called this one, are not delayed. Unlike [DelayAction], which postpones only the actions inside it,
 * a pause lets a sequence be written top to bottom: message, wait, message, wait, message.
 *
 * The pause takes effect when the list is run by [ActionContext.execute]; executing this action on its
 * own does nothing.
 *
 * @property duration how long to wait, not negative
 * @throws IllegalArgumentException when [duration] is negative
 */
data class WaitAction(
    val duration: Duration = Duration.ZERO,
) : Action {
    init { require(!duration.isNegative) { "Wait must not be negative" } }

    override fun execute(context: ActionContext) = Unit
}
