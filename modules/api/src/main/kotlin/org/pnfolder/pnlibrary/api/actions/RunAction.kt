package org.pnfolder.pnlibrary.api.actions

/**
 * Runs a named list of actions that the plugin defined once in its configuration.
 *
 * Use it to avoid repeating the same lines, for example a refusal sound and message used by many buttons.
 * The plugin gives the lists to [ActionService.context] as `templates`. A template may call another one,
 * up to [ActionContext.MAX_TEMPLATE_DEPTH] levels deep. An unknown name is a warning in the action logger
 * and execution continues.
 *
 * @property name template name, written as `[run] name`
 */
data class RunAction(
    val name: String = "",
) : Action {
    override fun execute(context: ActionContext) {
        context.runTemplate(name)
    }
}
