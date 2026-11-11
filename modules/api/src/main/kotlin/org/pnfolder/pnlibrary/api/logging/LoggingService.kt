package org.pnfolder.pnlibrary.api.logging

import org.pnfolder.pnlibrary.api.plugin.LifecycleRow

/** Semantic severity and presentation category for plugin log messages. */
enum class LogLevel {
    /** Detail for a support report; the console does not show it. */
    DEBUG,

    /** Normal operational information. */
    INFO,
    /** Positive completion or readiness information. */
    SUCCESS,
    /** Recoverable problem that may require administrator attention. */
    WARNING,
    /** Failed operation or unexpected exception. */
    ERROR,

    /** Failure that leaves the plugin or the server unable to do its job; always reaches the black box. */
    CRITICAL,
}

interface LoggingService {

    fun logger(owner: Any, name: String): PnLogger

    fun box(owner: Any, title: String): MessageBox

    fun shutdownBox(owner: Any, title: String): MessageBox

    fun box(owner: Any, name: String, version: String): MessageBox = box(owner, "$name $version")

    fun shutdownBox(owner: Any, name: String, version: String): MessageBox =
        shutdownBox(owner, "$name $version")
}

/**
 * Plugin-bound logger whose warnings and errors may be included in diagnostic reports.
 *
 * Messages are plain platform-console text. Implementations add the logger name and may
 * aggregate repeated diagnostic incidents before writing to the native console.
 */
interface PnLogger {
    /**
     * Writes one line at [level] and decides where it goes. The console always gets it. With [record] `true` (what every
     * short form below uses) it also goes into the black box: the diagnostics buffer, the journal and, for an
     * error with an exception, the error report. With [record] `false` it is console only and leaves no trace.
     */
    fun log(level: LogLevel, message: String, error: Throwable? = null, record: Boolean = true)

    /** Writes normal operational information to the console and the black box. */
    fun info(message: String) = log(LogLevel.INFO, message, null, true)

    /** Writes normal operational information; with [record] `false` it stays out of the black box. */
    fun info(message: String, record: Boolean) = log(LogLevel.INFO, message, null, record)

    /** Writes normal operational information together with the [error] that explains it, kept with its stack trace. */
    fun info(message: String, error: Throwable?) = log(LogLevel.INFO, message, error, true)

    /** Writes positive completion or readiness information to the console and the black box. */
    fun success(message: String) = log(LogLevel.SUCCESS, message, null, true)

    /** Writes positive completion information; with [record] `false` it stays out of the black box. */
    fun success(message: String, record: Boolean) = log(LogLevel.SUCCESS, message, null, record)

    /** Writes a recoverable problem requiring attention to the console and the black box. */
    fun warning(message: String) = log(LogLevel.WARNING, message, null, true)

    /** Writes a recoverable problem; with [record] `false` it stays out of the black box. */
    fun warning(message: String, record: Boolean) = log(LogLevel.WARNING, message, null, record)

    /** Writes a recoverable problem with the [error] that caused it, so the report keeps the stack trace. */
    fun warning(message: String, error: Throwable?) = log(LogLevel.WARNING, message, error, true)

    /** Writes a recoverable problem with its [error]; with [record] `false` it stays out of the black box. */
    fun warning(message: String, error: Throwable?, record: Boolean) = log(LogLevel.WARNING, message, error, record)

    /** Writes a failure to the console and the black box. */
    fun error(message: String) = log(LogLevel.ERROR, message, null, true)

    /** Writes a failure with its original [error], which the report keeps with the stack trace. */
    fun error(message: String, error: Throwable?) = log(LogLevel.ERROR, message, error, true)

    /** Writes a failure with its [error]; with [record] `false` it stays out of the black box. */
    fun error(message: String, error: Throwable?, record: Boolean) = log(LogLevel.ERROR, message, error, record)

    /** Writes a failure that stops the plugin from working, with the [error] behind it, to the console and the black box. */
    fun critical(message: String, error: Throwable? = null) = log(LogLevel.CRITICAL, message, error, true)

    /**
     * Writes a detail for a support report only: the black box keeps it and the console stays quiet. Use it where the
     * old code logged at `FINE`.
     */
    fun debug(message: String) = log(LogLevel.DEBUG, message, null, true)

    /** Writes a detail for a support report together with its [error]; the console stays quiet. */
    fun debug(message: String, error: Throwable?) = log(LogLevel.DEBUG, message, error, true)
}

/**
 * Fluent startup, shutdown, or operation summary displayed as one console block.
 *
 * Rows may be added until [show] is called. A box can be displayed exactly once, and
 * modifying or displaying it again fails with [IllegalStateException]. Failure rows
 * preserve their exception in the diagnostic log buffer.
 */
interface MessageBox {

    fun ok(label: String, detail: String): MessageBox

    fun warn(label: String, detail: String): MessageBox

    fun skip(label: String, detail: String): MessageBox

    fun fail(label: String, detail: String, error: Throwable? = null): MessageBox

    /**
     * Hides the rows pnLibrary adds by itself to a lifecycle card (enabled or disabled), for example
     * `lifecycle.enabled().hide(LifecycleRow.IDENTIFIER, LifecycleRow.NOT_CONFIGURED)`. The plugin's own rows are
     * never hidden, and on a generic box this does nothing.
     */
    fun hide(vararg rows: LifecycleRow): MessageBox = this

    /** Writes the complete box to the owning plugin's native console. */
    fun show()
}
