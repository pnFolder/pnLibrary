package org.pnfolder.pnlibrary.api.actions.builtin

import org.pnfolder.pnlibrary.api.actions.Action
import org.pnfolder.pnlibrary.api.actions.ActionContext

/**
 * Ends the current invocation: no action after this one runs, including the rest of any outer list.
 *
 * Use it inside the `then` or `else` branch of an `if` to leave early, the way a failed check
 * ends a menu click.
 */
class StopAction : Action {
    override fun execute(context: ActionContext) {
        context.stop()
    }
}
