package ru.privatenull.pnlibrary.api.metrics

/** FastStats capabilities owned by a live metrics session. */
interface FastStatsFacade {
    /** Native provider session. Register charts before start; use MetricsController.configure for live changes. */
    val metrics: PluginMetrics

    /** Reports handled failures; optional operation and attributes describe this occurrence. */
    fun errorTracker(): ErrorReporter
}
