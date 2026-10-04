package ru.privatenull.pnlibrary.spi.metrics

import java.nio.file.Path
import ru.privatenull.pnlibrary.api.metrics.ErrorReporter
import ru.privatenull.pnlibrary.api.metrics.PluginMetrics

/** Optional Java-21 FastStats implementation loaded only when the provider is selected. */
interface VelocityMetricsProvider {
    /** Opens a Velocity FastStats session using platform-native objects and [token]. */
    fun open(owner: Any, server: Any, logger: Any, dataDirectory: Path, token: String): PluginMetrics
    /** Opens the provider-backed error reporter. */
    fun openErrorReporter(): ErrorReporter
}
