package ru.privatenull.pnlibrary.api.logging

/** Semantic severity and presentation category for plugin log messages. */
enum class LogLevel {
    /** Normal operational information. */ INFO,
    /** Positive completion or readiness information. */ SUCCESS,
    /** Recoverable problem that may require administrator attention. */ WARNING,
    /** Failed operation or unexpected exception. */ ERROR,
}

/** Creates native loggers and formatted lifecycle summaries for one plugin. */
interface LoggingService {
    /** Creates a logger that prefixes messages with [name] and attributes them to [owner]. */
    fun logger(owner: Any, name: String): PnLogger
    /** Creates a generic one-shot message box with [title]. */
    fun box(owner: Any, title: String): MessageBox
    /** Creates a one-shot shutdown summary with [title]. */
    fun shutdownBox(owner: Any, title: String): MessageBox

    /** Creates an enabled box with explicit display metadata. */
    fun box(owner: Any, name: String, version: String): MessageBox = box(owner, "$name $version")

    /** Creates a disabled box with explicit display metadata. */
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
    /** Writes normal operational information. */
    fun info(message: String)
    /** Writes positive completion or readiness information. */
    fun success(message: String)
    /** Writes a recoverable problem requiring attention. */
    fun warning(message: String)
    /** Writes a failure with an optional original [error] for diagnostics. */
    fun error(message: String, error: Throwable? = null)
}

/**
 * Fluent startup, shutdown, or operation summary displayed as one console block.
 *
 * Rows may be added until [show] is called. A box can be displayed exactly once, and
 * modifying or displaying it again fails with [IllegalStateException]. Failure rows
 * preserve their exception in the diagnostic log buffer.
 */
interface MessageBox {
    /** Adds a successful subsystem row. */
    fun ok(label: String, detail: String): MessageBox
    /** Adds a recoverable-warning row. */
    fun warn(label: String, detail: String): MessageBox
    /** Adds a deliberately skipped subsystem row. */
    fun skip(label: String, detail: String): MessageBox
    /** Adds a failed row and optional original [error]. */
    fun fail(label: String, detail: String, error: Throwable? = null): MessageBox
    /** Writes the complete box to the owning plugin's native console. */
    fun show()
}
