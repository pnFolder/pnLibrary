package ru.privatenull.pnlibrary.api.logging

enum class LogLevel { INFO, SUCCESS, WARNING, ERROR }

/** Creates native loggers and formatted lifecycle summaries for one plugin. */
interface LoggingService {
    fun logger(owner: Any, name: String): PnLogger
    fun box(owner: Any, title: String): MessageBox
    fun shutdownBox(owner: Any, title: String): MessageBox

    /** Creates an enabled box with explicit display metadata. */
    fun box(owner: Any, name: String, version: String): MessageBox = box(owner, "$name $version")

    /** Creates a disabled box with explicit display metadata. */
    fun shutdownBox(owner: Any, name: String, version: String): MessageBox =
        shutdownBox(owner, "$name $version")
}

/** Logger whose warning and error messages can be included in `/pndebug --logs`. */
interface PnLogger {
    fun info(message: String)
    fun success(message: String)
    fun warning(message: String)
    fun error(message: String, error: Throwable? = null)
}

/** Fluent startup/shutdown summary. A box can be displayed exactly once. */
interface MessageBox {
    fun ok(label: String, detail: String): MessageBox
    fun warn(label: String, detail: String): MessageBox
    fun skip(label: String, detail: String): MessageBox
    fun fail(label: String, detail: String, error: Throwable? = null): MessageBox
    fun show()
}
