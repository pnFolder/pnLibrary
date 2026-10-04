package ru.privatenull.pnlibrary.bungee

import ru.privatenull.pnlibrary.api.metrics.ErrorReporter
import ru.privatenull.pnlibrary.api.metrics.TelemetryError
import dev.faststats.TrackedError
import ru.privatenull.pnlibrary.internal.faststats.FastStatsBridge

/** Bridges pnLibrary's sanitized error events into FastStats without coupling the public API to its SDK. */
class FastStatsErrorReporter : ErrorReporter {
    internal val tracker = FastStatsBridge.errorTracker()

    /** Converts [error] into a handled FastStats event with sanitized attributes. */
    override fun capture(error: TelemetryError) {
        val throwable = RuntimeException("${error.type}: ${error.message}").apply {
            stackTrace = error.stackTrace.map { frame ->
                StackTraceElement("reported", frame, null, -1)
            }.toTypedArray()
        }
        val tracked: TrackedError = tracker.trackError(throwable)
        tracked.handled(error.handled)
        error.operation?.let { tracked.attributes().put("operation", it) }
        error.attributes.forEach { (key, value) ->
            value?.let { tracked.attributes().put(key, it.toString()) }
        }
    }
    /** Performs no work because the owning metrics session controls the tracker lifecycle. */
    override fun close() = Unit
}
