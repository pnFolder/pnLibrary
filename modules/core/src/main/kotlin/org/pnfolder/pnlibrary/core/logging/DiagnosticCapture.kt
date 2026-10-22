package org.pnfolder.pnlibrary.core.logging

import org.pnfolder.pnlibrary.api.logging.LogLevel
import org.pnfolder.pnlibrary.spi.platform.PlatformLogging
import org.pnfolder.pnlibrary.spi.platform.PlatformOwnership

/** One capture path for library logs, service failures and observed native logs. */
internal class DiagnosticCapture(
    private val ownership: PlatformOwnership,
    private val native: PlatformLogging,
    private val incidents: DiagnosticLogBuffer? = null,
    private val journal: ((Any, LogLevel, String, Throwable?) -> Unit)? = null,
    private val onError: (Any, String, Throwable) -> Unit = { _, _, _ -> },
) : PlatformLogging {
    private val emitting = ThreadLocal<Boolean>()

    override fun log(owner: Any, level: LogLevel, message: String, error: Throwable?) {
        val result = capture(owner, level, message, error, true)
        val output = when {
            result == null || result.emitOriginal -> message
            result.summary != null -> result.summary
            else -> return
        }
        emit { native.log(owner, level, output, if (output == message) error else null) }
    }

    override fun console(owner: Any, message: String) = emit { native.console(owner, message) }

    /** Native observers already have console output; never emit it again or capture our own echo. */
    fun observe(owner: Any, level: LogLevel, message: String, error: Throwable?, includeJournal: Boolean) {
        if (emitting.get() == true) return
        capture(owner, level, message, error, includeJournal)
    }

    private fun capture(owner: Any, level: LogLevel, message: String, error: Throwable?, includeJournal: Boolean): DiagnosticLogBuffer.CaptureResult? {
        if (includeJournal) runCatching { journal?.invoke(owner, level, message, error) }
        if (error != null && level == LogLevel.ERROR) runCatching { onError(owner, message, error) }
        return runCatching { incidents?.record(ownership, owner, level, message, error) }.getOrNull()
    }

    private inline fun emit(action: () -> Unit) {
        val previous = emitting.get()
        emitting.set(true)
        try { action() } finally {
            if (previous == null) emitting.remove() else emitting.set(previous)
        }
    }
}
