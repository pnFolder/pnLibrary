package org.pnfolder.pnlibrary.api.scenario.actions

/**
 * How one [Action] went, and whether the rest of the list should run.
 *
 * ```kotlin
 * when (val result = actions.execute(context)) {
 *     ActionResult.Done -> player.sendMessage(Component.text("Bought"))
 *     is ActionResult.Stopped -> Unit                      // a requirement said no, already explained
 *     is ActionResult.Failed -> logger.warning(result.message)
 * }
 * ```
 */
sealed interface ActionResult {
    /** The step finished; the next one runs. */
    data object Done : ActionResult

    /**
     * The scenario ends on purpose, for example because a requirement was not met. Nothing is undone.
     *
     * @property reason short readable cause, or `null`
     */
    data class Stopped(val reason: String? = null) : ActionResult

    /**
     * The step could not do its job. The scenario ends and steps that registered an undo, such as a charge,
     * give back what they took (see [ActionContext.onUndo]).
     *
     * @property code stable machine-readable cause such as `currency.insufficient-funds`
     * @property message readable cause for logs and players
     * @property cause original exception, when there was one
     */
    data class Failed(val code: String, val message: String, val cause: Throwable? = null) : ActionResult
}
