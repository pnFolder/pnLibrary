package ru.privatenull.pnlibrary.api.actions

import ru.privatenull.pnlibrary.api.logging.LogLevel

/** Writes a line through the owning plugin logger. */
data class ConsoleLogAction(
    val text: String = "",
    val level: LogLevel = LogLevel.INFO,
) : Action {
    override fun execute(context: Action.Context) = when (level) {
        LogLevel.INFO -> context.logger.info(text)
        LogLevel.SUCCESS -> context.logger.success(text)
        LogLevel.WARNING -> context.logger.warning(text)
        LogLevel.ERROR -> context.logger.error(text)
    }
}
