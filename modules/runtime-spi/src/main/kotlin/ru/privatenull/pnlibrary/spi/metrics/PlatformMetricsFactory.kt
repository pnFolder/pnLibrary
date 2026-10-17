package ru.privatenull.pnlibrary.spi.metrics

import ru.privatenull.pnlibrary.api.metrics.ErrorReporter
import ru.privatenull.pnlibrary.api.metrics.MetricsProvider
import ru.privatenull.pnlibrary.api.metrics.MetricsProviderConfiguration
import ru.privatenull.pnlibrary.api.metrics.PluginMetrics
import java.util.function.Supplier

/**
 * Runtime-only bridge that creates platform-specific metrics sessions.
 *
 * Each successful [open] call returns an independently closeable session bound to its native
 * owner. The factory should validate credentials before allocating native resources and
 * propagate startup failures so the caller can roll back plugin registration.
 */
interface PlatformMetricsFactory {
    /** Opens the optional provider-backed error reporter. */
    fun openErrorReporter(owner: Any, configurations: Collection<MetricsProviderConfiguration>): ErrorReporter? = null

    /** Opens one session covering every enabled provider in [configurations]. */
    fun open(owner: Any, configurations: Collection<MetricsProviderConfiguration>): PluginMetrics
}

/**
 * Metrics factory used by platforms without a native metrics adapter.
 *
 * The returned session reports the enabled providers but discards all chart registrations.
 * This allows shared runtime code to remain branch-free when metrics are unavailable.
 */
object NoopMetricsFactory : PlatformMetricsFactory {
    override fun open(owner: Any, configurations: Collection<MetricsProviderConfiguration>): PluginMetrics =
        object : PluginMetrics {
            override val providers: Set<MetricsProvider> =
                configurations.filter { it.enabled }.map { it.provider }.toSet()
            override fun simplePie(id: String, value: Supplier<String?>) = this
            override fun advancedPie(id: String, values: Supplier<Map<String, Int>>) = this
            override fun drilldownPie(id: String, values: Supplier<Map<String, Map<String, Int>>>) = this
            override fun singleLineChart(id: String, value: Supplier<Int>) = this
            override fun multiLineChart(id: String, values: Supplier<Map<String, Int>>) = this
            override fun simpleBarChart(id: String, values: Supplier<Map<String, Int>>) = this
            override fun advancedBarChart(id: String, values: Supplier<Map<String, IntArray>>) = this
            override fun close() = Unit
        }
}
