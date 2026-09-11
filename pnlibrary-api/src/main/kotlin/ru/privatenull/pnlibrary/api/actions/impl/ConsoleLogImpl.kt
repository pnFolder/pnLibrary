package ru.privatenull.pnlibrary.api.actions.impl

import ru.privatenull.pnlibrary.api.actions.Action
import ru.privatenull.pnlibrary.api.logging.LogLevel

class ConsoleLogImpl(
    var text: String = "",
    var level: LogLevel = LogLevel.INFO,
) : Action {
    override fun execute(context: Action.Context) = when (level) {
        LogLevel.INFO -> context.logger.info(text)
        LogLevel.SUCCESS -> context.logger.success(text)
        LogLevel.WARNING -> context.logger.warning(text)
        LogLevel.ERROR -> context.logger.error(text)
    }
}
