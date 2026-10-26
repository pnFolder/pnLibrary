package org.pnfolder.pnlibrary.spi.platform

import org.pnfolder.pnlibrary.api.logging.LogLevel

/** Native logging boundary used by shared runtime services. */
interface PlatformLogging {
    /** Sink for log lines; the default prints to the process console. */
    val logHandler: (Any, LogLevel, String, Throwable?) -> Unit
        get() = { _, level, message, error ->
            val line = "[pnLibrary/${level.name}] $message"
            if (level == LogLevel.ERROR) System.err.println(line) else System.out.println(line)
            error?.printStackTrace(System.err)
        }

    /** Logs [message] for [owner] at [level], attaching [error] when present. */
    fun log(owner: Any, level: LogLevel, message: String, error: Throwable? = null) {
        logHandler(owner, level, message, error)
    }

    /** Prints [message] to the server console at info level. */
    fun console(owner: Any, message: String) = log(owner, LogLevel.INFO, message)
}
