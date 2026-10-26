package org.pnfolder.pnlibrary.core.logging

import org.pnfolder.pnlibrary.api.logging.LogLevel
import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
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
    private val echoes = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private companion object {
        const val ECHO_WINDOW_NANOS = 2_000_000_000L
        const val ECHO_LIMIT = 512
    }

    override fun log(owner: Any, level: LogLevel, message: String, error: Throwable?) {
        val result = capture(owner, level, message, error, true)
        val output = when {
            result == null || result.emitOriginal -> message
            result.summary != null -> result.summary
            else -> return
        }
        rememberEcho(level, output)
        emit { native.log(owner, level, output, if (output == message) error else null) }
    }

    override fun console(owner: Any, message: String) = emit { native.console(owner, message) }

    /** Native observers already have console output; never emit it again or capture our own echo. */
    fun observe(owner: Any, level: LogLevel, message: String, error: Throwable?, includeJournal: Boolean) {
        if (emitting.get() == true || echoedRecently(level, message)) return
        capture(owner, level, message, error, includeJournal)
    }

    /** A native logger may echo our line on another thread, where the thread-local guard cannot see it. */
    private fun echoedRecently(level: LogLevel, message: String): Boolean {
        val emittedAt = echoes["$level|$message"] ?: return false
        return System.nanoTime() - emittedAt < ECHO_WINDOW_NANOS
    }

    private fun rememberEcho(level: LogLevel, message: String) {
        val now = System.nanoTime()
        if (echoes.size > ECHO_LIMIT) echoes.entries.removeIf { now - it.value >= ECHO_WINDOW_NANOS }
        if (echoes.size <= ECHO_LIMIT) echoes["$level|$message"] = now
    }

    private fun capture(owner: Any, level: LogLevel, message: String, error: Throwable?, includeJournal: Boolean): DiagnosticLogBuffer.CaptureResult? {
        if (includeJournal) runCatchingNonFatal { journal?.invoke(owner, level, message, error) }
        if (error != null && level == LogLevel.ERROR) runCatchingNonFatal { onError(owner, message, error) }
        return runCatchingNonFatal { incidents?.record(ownership, owner, level, message, error) }.getOrNull()
    }

    private inline fun emit(action: () -> Unit) {
        val previous = emitting.get()
        emitting.set(true)
        try { action() } finally {
            if (previous == null) emitting.remove() else emitting.set(previous)
        }
    }
}
