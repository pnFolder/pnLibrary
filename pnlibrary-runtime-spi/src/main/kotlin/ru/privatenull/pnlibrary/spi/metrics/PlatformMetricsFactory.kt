package ru.privatenull.pnlibrary.spi.metrics

import ru.privatenull.pnlibrary.api.metrics.PluginMetrics
import java.util.function.Supplier

/** Runtime-only bridge to a platform-specific metrics implementation. */
interface PlatformMetricsFactory {
    fun open(owner: Any, projectId: Int): PluginMetrics
}

/** Metrics factory used by platforms without a native bStats adapter. */
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
