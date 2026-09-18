package ru.privatenull.pnlibrary.api.actions

import ru.privatenull.pnlibrary.api.logging.LogLevel

/**
 * Writes plain text through the logger associated with the action invocation.
 *
 * The text is not parsed as an Adventure component and no placeholder substitution is
 * performed by this action.
 *
 * @property text message passed to the logger
 * @property level logging method selected for the message
 */
data class ConsoleLogAction(
    val text: String = "",
    val level: LogLevel = LogLevel.INFO,
) : Action {
    override fun execute(context: ActionContext) = when (level) {
        LogLevel.INFO -> context.logger.info(text)
        LogLevel.SUCCESS -> context.logger.success(text)
        LogLevel.WARNING -> context.logger.warning(text)
        LogLevel.ERROR -> context.logger.error(text)
    }
}
