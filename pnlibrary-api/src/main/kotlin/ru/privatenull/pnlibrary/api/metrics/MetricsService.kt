package ru.privatenull.pnlibrary.api.metrics

import java.util.function.Supplier

/** Public pnLibrary abstraction over the platform bStats implementation. */
interface MetricsService {
    fun open(owner: Any, projectId: Int): PluginMetrics
}

interface PluginMetrics : AutoCloseable {
    val projectId: Int
    fun simplePie(id: String, value: Supplier<String?>): PluginMetrics
    fun advancedPie(id: String, values: Supplier<Map<String, Int>>): PluginMetrics
    fun drilldownPie(id: String, values: Supplier<Map<String, Map<String, Int>>>): PluginMetrics
    fun singleLineChart(id: String, value: Supplier<Int>): PluginMetrics
    fun multiLineChart(id: String, values: Supplier<Map<String, Int>>): PluginMetrics
    fun simpleBarChart(id: String, values: Supplier<Map<String, Int>>): PluginMetrics
    fun advancedBarChart(id: String, values: Supplier<Map<String, IntArray>>): PluginMetrics
    override fun close()
}

/** Internal platform bridge. Consumers use [MetricsService]. */
interface PlatformMetricsFactory {
    fun open(owner: Any, projectId: Int): PluginMetrics
}

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
