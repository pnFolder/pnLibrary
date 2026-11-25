package org.pnfolder.pnlibrary.api.logging

/** A [PnLogger] that adds [facts] to every line it writes, then passes the line to [base]. */
internal class ContextPnLogger(private val base: PnLogger, private val facts: Map<String, Any?>) : PnLogger {
    override fun log(level: LogLevel, message: String, error: Throwable?, context: Map<String, Any?>) =
        base.log(level, message, error, facts + context)

    override fun record(level: LogLevel, message: String, error: Throwable?) = base.record(level, message, error)
}
