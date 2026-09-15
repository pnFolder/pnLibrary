package ru.privatenull.pnlibrary.spi.metrics

import ru.privatenull.pnlibrary.api.metrics.PluginMetrics
import java.util.function.Supplier

/**
 * Runtime-only bridge that creates platform-specific metrics sessions.
 *
 * Each successful [open] call returns an independently closeable session bound to its native
 * owner. The factory should validate the project ID before allocating native resources and
 * propagate startup failures so the caller can roll back plugin registration.
 */
interface PlatformMetricsFactory {
    /** Opens a metrics session for one native plugin owner and bStats project. */
    fun open(owner: Any, projectId: Int): PluginMetrics
}

/**
 * Metrics factory used by platforms without a native bStats adapter.
 *
 * The returned session preserves [PluginMetrics.projectId] but discards all chart registrations.
 * This allows shared runtime code to remain branch-free when metrics are unavailable.
 */
object NoopMetricsFactory : PlatformMetricsFactory {
    override fun open(owner: Any, projectId: Int): PluginMetrics = object : PluginMetrics {
        override val projectId = projectId
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
