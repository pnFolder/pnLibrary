package org.pnfolder.pnlibrary.api.scenario.actions.builtin

import org.pnfolder.pnlibrary.api.logging.LogLevel
import org.pnfolder.pnlibrary.api.scenario.actions.Action
import org.pnfolder.pnlibrary.api.scenario.actions.ActionContext
import org.pnfolder.pnlibrary.api.scenario.actions.ActionResult

/**
 * Writes a line to the plugin log.
 *
 * @property text the line; placeholders are filled in
 * @property level severity
 */
data class LogAction(
    val text: String = "",
    val level: LogLevel = LogLevel.INFO,
) : Action {
    override fun run(context: ActionContext): ActionResult {
        val line = context.format(text)
        when (level) {
            LogLevel.DEBUG -> context.logger.debug(line)
            LogLevel.INFO -> context.logger.info(line)
            LogLevel.SUCCESS -> context.logger.success(line)
            LogLevel.WARNING -> context.logger.warning(line)
            LogLevel.ERROR -> context.logger.error(line)
            LogLevel.CRITICAL -> context.logger.critical(line)
        }
        return ActionResult.Done
    }
}
