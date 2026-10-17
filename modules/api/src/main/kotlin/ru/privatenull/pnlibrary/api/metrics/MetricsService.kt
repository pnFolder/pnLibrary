package ru.privatenull.pnlibrary.api.metrics

import java.util.function.Supplier

/** Public pnLibrary abstraction over the platform-specific metrics providers. */
interface MetricsService {
    /** Providers available in the current platform runtime. */
    val providers: Set<MetricsProvider>
        get() = setOf(MetricsProvider.BSTATS)

    /**
     * Opens and owns one metrics session for a native plugin [owner].
     *
     * Every enabled entry of [configurations] contributes one provider to the returned session.
     *
     * @throws IllegalArgumentException when no configuration is enabled or a provider lacks credentials
     */
    fun open(owner: Any, configurations: Collection<MetricsProviderConfiguration>): PluginMetrics

    /** Opens the optional provider-backed error reporter for the same configuration. */
    fun openErrorReporter(owner: Any, configurations: Collection<MetricsProviderConfiguration>): ErrorReporter? = null
}

/**
 * Live plugin metrics session with fluent registration of standard charts.
 *
 * Suppliers are evaluated by the platform metrics implementation, potentially after
 * registration and on a background thread. They should therefore be thread-safe,
 * non-blocking, and free of destructive side effects. Closing a managed session is safe
 * to repeat and prevents its registry from retaining the session.
 */
interface PluginMetrics : AutoCloseable {
    /** Providers represented by this session; more than one when several are enabled. */
    val providers: Set<MetricsProvider>

    /** Error reporter owned by this session; absent when no provider supports error tracking. */
    val errorReporter: ErrorReporter?
        get() = null

    /** Starts provider submission after all configured charts and services are registered. */
    fun start() = Unit

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
