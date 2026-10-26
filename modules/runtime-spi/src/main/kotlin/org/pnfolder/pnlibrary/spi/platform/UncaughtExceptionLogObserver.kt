package org.pnfolder.pnlibrary.spi.platform

import org.pnfolder.pnlibrary.api.logging.LogLevel

/**
 * Observes uncaught process exceptions, preserving the handler installed before this bridge.
 *
 * [owner] identifies the observer, not necessarily the plugin that caused the exception.
 * This does not intercept SLF4J output or exceptions already handled by native schedulers.
 */
class UncaughtExceptionLogObserver(private val owner: Any) {
    @Volatile
    private var callback: ((Any, LogLevel, String, Throwable?) -> Unit)? = null
    private var previous: Thread.UncaughtExceptionHandler? = null
    private var installed: Thread.UncaughtExceptionHandler? = null

    @Synchronized
    /** Installs or removes the uncaught-exception hook; `null` restores the previous handler. */
    fun observe(observer: ((Any, LogLevel, String, Throwable?) -> Unit)?) {
        callback = observer
        if (observer != null && installed == null) {
            previous = Thread.getDefaultUncaughtExceptionHandler()
            val predecessor = previous
            installed = Thread.UncaughtExceptionHandler { thread, error ->
                try {
                    callback?.invoke(owner, LogLevel.ERROR, "Uncaught process exception on thread ${thread.name}", error)
                } finally {
                    if (predecessor != null) predecessor.uncaughtException(thread, error)
                    else error.printStackTrace()
                }
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
