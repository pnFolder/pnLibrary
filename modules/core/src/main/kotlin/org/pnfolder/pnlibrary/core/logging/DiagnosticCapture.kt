package org.pnfolder.pnlibrary.core.logging

import org.pnfolder.pnlibrary.api.logging.LogLevel
import org.pnfolder.pnlibrary.common.concurrent.FailureThrottle
import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
import org.pnfolder.pnlibrary.spi.platform.PlatformLogging
import org.pnfolder.pnlibrary.spi.platform.PlatformOwnership

/** Wraps platform logging: records warnings and errors into the incident buffer and the journal while passing output through. */
internal class DiagnosticCapture(
    private val ownership: PlatformOwnership,
    private val native: PlatformLogging,
    private val incidents: DiagnosticLogBuffer? = null,
    private val journal: ((Any, LogLevel, String, Throwable?) -> Unit)? = null,
    private val onError: (Any, String, Throwable) -> Unit = { _, _, _ -> },
) : PlatformLogging {
    private val emitting = ThreadLocal<Boolean>()
    private val echoes = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val stageFailures = FailureThrottle()

    private companion object {
        const val ECHO_WINDOW_NANOS = 2_000_000_000L
        const val ECHO_LIMIT = 512
    }

    override fun log(owner: Any, level: LogLevel, message: String, error: Throwable?) =
        write(owner, level, message, error, true)

    /**
     * Writes one line. With [record] `false` it is console only and never reaches the black box; a [LogLevel.DEBUG]
     * line is the opposite and never reaches the console.
     */
    fun write(owner: Any, level: LogLevel, message: String, error: Throwable? = null, record: Boolean = true) {
        val result = if (record) capture(owner, level, message, error, true) else null
        if (level == LogLevel.DEBUG) return
        val output = when {
            result == null || result.emitOriginal -> message
            result.summary != null -> result.summary
            else -> return
        }
        rememberEcho(level, output)
        emit { native.log(owner, level, ConsoleStyle.decorate(level, output), if (output == message) error else null) }
    }

    override fun console(owner: Any, message: String) = emit { native.console(owner, message) }

    /** Native observers already have console output; never emit it again or capture our own echo. */
    fun observe(owner: Any, level: LogLevel, message: String, error: Throwable?) = observeLine(owner, level, message, error, true)

    /** Like [observe] for lines of other plugins: they reach the black box but not the journal. */
    fun observeWithoutJournal(owner: Any, level: LogLevel, message: String, error: Throwable?) =
        observeLine(owner, level, message, error, false)

    private fun observeLine(owner: Any, level: LogLevel, message: String, error: Throwable?, includeJournal: Boolean) {
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
        if (includeJournal) guarded(owner, "journal") { journal?.invoke(owner, level, message, error) }
        // An error logged as text only still has to reach the error tracker, which takes throwables.
        if (level >= LogLevel.ERROR) guarded(owner, "error report") { onError(owner, message, error ?: LoggedError(message)) }
        return guarded(owner, "incident buffer") { incidents?.record(ownership, owner, level, message, error) }
    }

    /**
     * A broken stage must not stop the line or the other stages, but it must not stay unseen either. The report goes to
     * the console only: recording it would run the broken stage again.
     */
    private inline fun <T> guarded(owner: Any, stage: String, action: () -> T): T? =
        runCatchingNonFatal(action).onFailure { reportStageFailure(owner, stage, it) }.getOrNull()

    private fun reportStageFailure(owner: Any, stage: String, error: Throwable) {
        if (!stageFailures.shouldReport(stage)) return
        val message = "pnLibrary could not pass a log line to the $stage: ${error.javaClass.simpleName}: ${error.message}"
        rememberEcho(LogLevel.ERROR, message)
        emit { native.log(owner, LogLevel.ERROR, ConsoleStyle.decorate(LogLevel.ERROR, message), error) }
    }

    private inline fun emit(action: () -> Unit) {
        val previous = emitting.get()
        emitting.set(true)
        try { action() } finally {
            if (previous == null) emitting.remove() else emitting.set(previous)
        }
    }

    /** Stands in for the missing exception of an error logged as plain text. */
    private class LoggedError(message: String) : RuntimeException(message)
}
