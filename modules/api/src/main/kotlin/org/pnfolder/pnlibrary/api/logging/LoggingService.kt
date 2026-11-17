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

/** Creates plugin-bound loggers and console message boxes. */
interface LoggingService {

    /** Returns the logger called [name] that belongs to the plugin [owner]. */
    fun logger(owner: Any, name: String): PnLogger

    /** Starts a startup summary block titled [title] for [owner]; nothing is printed until `show()`. */
    fun box(owner: Any, title: String): MessageBox

    /** Starts a shutdown summary block titled [title] for [owner]. */
    fun shutdownBox(owner: Any, title: String): MessageBox

    /** Same as [box] with the title `name version`. */
    fun box(owner: Any, name: String, version: String): MessageBox = box(owner, "$name $version")

    /** Same as [shutdownBox] with the title `name version`. */
    fun shutdownBox(owner: Any, name: String, version: String): MessageBox =
        shutdownBox(owner, "$name $version")
}

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

/**
 * Fluent startup, shutdown, or operation summary displayed as one console block.
 *
 * Rows may be added until [show] is called. A box can be displayed exactly once, and
 * modifying or displaying it again fails with [IllegalStateException]. Failure rows
 * preserve their exception in the diagnostic log buffer.
 */
interface MessageBox {

    /** Adds a successful row. */
    fun ok(label: String, detail: String): MessageBox

    /** Adds a row for something that works but needs attention. */
    fun warn(label: String, detail: String): MessageBox

    /** Adds a row for a step that was skipped on purpose. */
    fun skip(label: String, detail: String): MessageBox

    /** Adds a failed row; [error], when given, is kept in the diagnostic log buffer. */
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
