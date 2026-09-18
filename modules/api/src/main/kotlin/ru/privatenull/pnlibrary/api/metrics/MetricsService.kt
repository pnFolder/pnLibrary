package ru.privatenull.pnlibrary.api.metrics

import java.util.function.Supplier

/** Public pnLibrary abstraction over the platform-specific bStats implementation. */
interface MetricsService {
    /**
     * Opens and owns one metrics session for a native plugin [owner].
     *
     * @param projectId positive bStats project identifier assigned to that plugin
     * @throws IllegalArgumentException when [projectId] is not positive
     */
    fun open(owner: Any, projectId: Int): PluginMetrics
}

/**
 * Live plugin metrics session with fluent registration of standard bStats charts.
 *
 * Suppliers are evaluated by the platform metrics implementation, potentially after
 * registration and on a background thread. They should therefore be thread-safe,
 * non-blocking, and free of destructive side effects. Closing a managed session is safe
 * to repeat and prevents its registry from retaining the session.
 */
interface PluginMetrics : AutoCloseable {
    /** Positive bStats project identifier associated with this session. */
    val projectId: Int
    /** Registers a single categorical string value; `null` omits the current sample. */
    fun simplePie(id: String, value: Supplier<String?>): PluginMetrics
    /** Registers category-to-count values for one pie chart. */
    fun advancedPie(id: String, values: Supplier<Map<String, Int>>): PluginMetrics
    /** Registers two-level category-to-count values for a drilldown chart. */
    fun drilldownPie(id: String, values: Supplier<Map<String, Map<String, Int>>>): PluginMetrics
    /** Registers one integer sample for a line chart. */
    fun singleLineChart(id: String, value: Supplier<Int>): PluginMetrics
    /** Registers named integer samples for a multiline chart. */
    fun multiLineChart(id: String, values: Supplier<Map<String, Int>>): PluginMetrics
    /** Registers named integer bars for a simple bar chart. */
    fun simpleBarChart(id: String, values: Supplier<Map<String, Int>>): PluginMetrics
    /** Registers named arrays of integer bars for an advanced bar chart. */
    fun advancedBarChart(id: String, values: Supplier<Map<String, IntArray>>): PluginMetrics
    /** Stops this metrics session and releases its registry ownership. */
    override fun close()
}
