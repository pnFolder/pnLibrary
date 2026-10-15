package ru.privatenull.pnlibrary.spi.platform

import ru.privatenull.pnlibrary.api.logging.LogLevel

/** Native logging boundary used by shared runtime services. */
interface PlatformLogging {
    val logHandler: (Any, LogLevel, String, Throwable?) -> Unit
        get() = { _, level, message, error ->
            val line = "[pnLibrary/${level.name}] $message"
            if (level == LogLevel.ERROR) System.err.println(line) else System.out.println(line)
            error?.printStackTrace(System.err)
        }

    fun log(owner: Any, level: LogLevel, message: String, error: Throwable? = null) {
        logHandler(owner, level, message, error)
    }

    fun console(owner: Any, message: String) = log(owner, LogLevel.INFO, message)
}
