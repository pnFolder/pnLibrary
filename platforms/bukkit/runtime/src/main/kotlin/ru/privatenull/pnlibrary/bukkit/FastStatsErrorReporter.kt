package ru.privatenull.pnlibrary.bukkit

import ru.privatenull.pnlibrary.api.metrics.ErrorReporter
import ru.privatenull.pnlibrary.api.metrics.TelemetryError
import dev.faststats.TrackedError
import ru.privatenull.pnlibrary.internal.faststats.FastStatsBridge

/** Bridges pnLibrary's sanitized error events into FastStats without exposing its SDK in the API. */
class FastStatsErrorReporter : ErrorReporter {
    private val tracker = FastStatsBridge.errorTracker()

    override fun capture(error: TelemetryError) {
        val throwable = RuntimeException("${error.type}: ${error.message}").apply {
            error.stackTrace.firstOrNull()?.let { stackTrace = arrayOf(StackTraceElement("pnLibrary", "capture", "Telemetry", 0)) }
        }
        val tracked: TrackedError = tracker.trackError(throwable)
        tracked.handled(error.handled)
    }

    override fun close() = Unit
}
