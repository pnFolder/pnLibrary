package org.pnfolder.pnlibrary.api.logging

/**
 * Plugin-bound logger that writes to the console and the black box (journal, diagnostics buffer, error report).
 *
 * Every line reaches the black box. Pass [context] to attach structured key-value facts that are stored alongside
 * the message in the journal and appear in support reports.
 */
interface PnLogger {
    /** Writes one line at [level] to the console and the black box. [context] is stored as extra key-value facts. */
    fun log(level: LogLevel, message: String, error: Throwable? = null, context: Map<String, Any?> = emptyMap())

    fun info(message: String) = log(LogLevel.INFO, message)
    fun info(message: String, error: Throwable?) = log(LogLevel.INFO, message, error)
    fun info(message: String, context: Map<String, Any?>) = log(LogLevel.INFO, message, context = context)

    fun success(message: String) = log(LogLevel.SUCCESS, message)
    fun success(message: String, context: Map<String, Any?>) = log(LogLevel.SUCCESS, message, context = context)

    fun warning(message: String) = log(LogLevel.WARNING, message)
    fun warning(message: String, error: Throwable?) = log(LogLevel.WARNING, message, error)
    fun warning(message: String, context: Map<String, Any?>) = log(LogLevel.WARNING, message, context = context)
    fun warning(message: String, error: Throwable?, context: Map<String, Any?>) = log(LogLevel.WARNING, message, error, context)

    fun error(message: String) = log(LogLevel.ERROR, message)
    fun error(message: String, error: Throwable?) = log(LogLevel.ERROR, message, error)
    fun error(message: String, error: Throwable?, context: Map<String, Any?>) = log(LogLevel.ERROR, message, error, context)

    /** Writes a failure that stops the plugin from working; always reaches the black box regardless of retention. */
    fun critical(message: String, error: Throwable? = null) = log(LogLevel.CRITICAL, message, error)
    fun critical(message: String, error: Throwable?, context: Map<String, Any?>) = log(LogLevel.CRITICAL, message, error, context)

    /** Writes a detail for a support report only; the console stays quiet. */
    fun debug(message: String) = log(LogLevel.DEBUG, message)
    fun debug(message: String, error: Throwable?) = log(LogLevel.DEBUG, message, error)
    fun debug(message: String, context: Map<String, Any?>) = log(LogLevel.DEBUG, message, context = context)

    /**
     * Records a line in the black box at [level] without printing to the console.
     * Use to replay buffered pre-startup entries after the library has initialised.
     */
    fun record(level: LogLevel, message: String, error: Throwable? = null) = debug(message, error)
}
