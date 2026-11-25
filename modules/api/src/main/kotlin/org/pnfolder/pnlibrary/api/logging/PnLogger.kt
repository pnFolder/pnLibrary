package org.pnfolder.pnlibrary.api.logging

/**
 * Plugin-bound logger that writes to the console and the black box (journal, diagnostics buffer, error report).
 *
 * Every line reaches the black box. To attach structured key-value facts that are stored alongside the message in the
 * journal and appear in support reports, get a logger that carries them with [with]:
 *
 * ```kotlin
 * logger.warning("Shop is empty")
 * logger.error("Save failed", exception)
 * logger.with("player" to player.name, "shop" to id).warning("Purchase refused")
 * ```
 */
interface PnLogger {
    /** Writes one line at [level] to the console and the black box. [context] is stored as extra key-value facts. */
    fun log(level: LogLevel, message: String, error: Throwable? = null, context: Map<String, Any?> = emptyMap())

    /** Writes an ordinary line. */
    fun info(message: String) = log(LogLevel.INFO, message)

    /** Writes an ordinary line with the failure that explains it. */
    fun info(message: String, error: Throwable?) = log(LogLevel.INFO, message, error)

    /** Writes a line that something worked. */
    fun success(message: String) = log(LogLevel.SUCCESS, message)

    /** Writes a line about something that is not right but does not stop the plugin. */
    fun warning(message: String) = log(LogLevel.WARNING, message)

    /** Writes a warning with the failure that explains it. */
    fun warning(message: String, error: Throwable?) = log(LogLevel.WARNING, message, error)

    /** Writes a failure of one action. */
    fun error(message: String) = log(LogLevel.ERROR, message)

    /** Writes a failure of one action with its cause. */
    fun error(message: String, error: Throwable?) = log(LogLevel.ERROR, message, error)

    /** Writes a failure that stops the plugin from working; always reaches the black box regardless of retention. */
    fun critical(message: String, error: Throwable? = null) = log(LogLevel.CRITICAL, message, error)

    /** Writes a detail for a support report only; the console stays quiet. */
    fun debug(message: String) = log(LogLevel.DEBUG, message)

    /** Writes a detail with the failure that explains it, for a support report only. */
    fun debug(message: String, error: Throwable?) = log(LogLevel.DEBUG, message, error)

    /** A logger that adds [context] to every line it writes. */
    fun with(context: Map<String, Any?>): PnLogger = ContextPnLogger(this, context)

    /** A logger that adds [facts] (name to value) to every line it writes. */
    fun with(vararg facts: Pair<String, Any?>): PnLogger = with(facts.toMap())

    /**
     * Records a line in the black box at [level] without printing to the console.
     * Use to replay buffered pre-startup entries after the library has initialised.
     */
    fun record(level: LogLevel, message: String, error: Throwable? = null) = debug(message, error)
}
