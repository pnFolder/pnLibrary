package ru.privatenull.pnlibrary.spi.platform

import ru.privatenull.pnlibrary.api.logging.LogLevel

/** Installs a process-level uncaught-exception bridge while preserving the previous handler. */
class UncaughtExceptionLogObserver(private val owner: Any) {
    private var previous: Thread.UncaughtExceptionHandler? = null
    private var installed: Thread.UncaughtExceptionHandler? = null

    @Synchronized
    fun observe(observer: ((Any, LogLevel, String, Throwable?) -> Unit)?) {
        if (observer != null && installed == null) {
            previous = Thread.getDefaultUncaughtExceptionHandler()
            installed = Thread.UncaughtExceptionHandler { thread, error ->
                observer(owner, LogLevel.ERROR, "Uncaught exception on thread ${thread.name}", error)
                previous?.uncaughtException(thread, error)
            }
            Thread.setDefaultUncaughtExceptionHandler(installed)
        } else if (observer == null && installed != null) {
            if (Thread.getDefaultUncaughtExceptionHandler() === installed) {
                Thread.setDefaultUncaughtExceptionHandler(previous)
            }
            previous = null
            installed = null
        }
    }
}
