package ru.privatenull.pnlibrary.bungee

import ru.privatenull.pnlibrary.api.metrics.ErrorReporter
import ru.privatenull.pnlibrary.api.metrics.TelemetryError
import dev.faststats.TrackedError
import ru.privatenull.pnlibrary.internal.faststats.FastStatsBridge

/** Bridges pnLibrary's sanitized error events into FastStats without coupling the public API to its SDK. */
class FastStatsErrorReporter : ErrorReporter {
    private val tracker = FastStatsBridge.errorTracker()
    override fun capture(error: TelemetryError) {
        val tracked: TrackedError = tracker.trackError(RuntimeException("${error.type}: ${error.message}"))
        tracked.handled(error.handled)
    }
    override fun close() = Unit
}
