package ru.privatenull.pnlibrary.bukkit

import ru.privatenull.pnlibrary.api.metrics.ErrorReporter
import ru.privatenull.pnlibrary.api.metrics.TelemetryError
import dev.faststats.TrackedError
import ru.privatenull.pnlibrary.internal.faststats.FastStatsBridge

/** Bridges pnLibrary's sanitized error events into FastStats without exposing its SDK in the API. */
class FastStatsErrorReporter : ErrorReporter {
    internal val tracker = FastStatsBridge.errorTracker()

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

    override fun close() = Unit
}
