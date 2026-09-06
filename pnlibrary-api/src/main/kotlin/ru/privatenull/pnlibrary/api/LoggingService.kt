package ru.privatenull.pnlibrary.api

enum class LogLevel { INFO, SUCCESS, WARNING, ERROR }

interface LoggingService {
    fun logger(owner: Any, name: String): PnLogger
    fun box(owner: Any, title: String): MessageBox
    fun shutdownBox(owner: Any, title: String): MessageBox
}

interface PnLogger {
    fun info(message: String)
    fun success(message: String)
    fun warning(message: String)
    fun error(message: String, error: Throwable? = null)
}

interface MessageBox {
    fun ok(label: String, detail: String): MessageBox
    fun warn(label: String, detail: String): MessageBox
    fun skip(label: String, detail: String): MessageBox
    fun fail(label: String, detail: String, error: Throwable? = null): MessageBox
    fun show()
}
