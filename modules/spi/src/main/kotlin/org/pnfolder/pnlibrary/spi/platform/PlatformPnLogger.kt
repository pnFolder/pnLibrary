package org.pnfolder.pnlibrary.spi.platform

import org.pnfolder.pnlibrary.api.logging.LogLevel
import org.pnfolder.pnlibrary.api.logging.PnLogger

/**
 * A [PnLogger] for runtime parts that hold a [PlatformLogging] instead of a plugin logger. Lines carry the [name]
 * and go where [logging] sends them; when it is the library's recording sink, that is the black box too.
 * Context keys are not forwarded since [PlatformLogging] does not carry structured fields.
 */
class PlatformPnLogger(
    private val logging: PlatformLogging,
    private val owner: Any,
    private val name: String,
) : PnLogger {
    override fun log(level: LogLevel, message: String, error: Throwable?, context: Map<String, Any?>) =
        logging.log(owner, level, "[$name] $message", error)
}
