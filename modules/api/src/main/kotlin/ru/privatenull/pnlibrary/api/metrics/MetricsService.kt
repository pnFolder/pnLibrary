package ru.privatenull.pnlibrary.api.metrics

import java.util.function.Supplier

/** Public pnLibrary abstraction over the platform-specific bStats implementation. */
interface MetricsService {
    /** Providers available in the current platform runtime. */
    val providers: Set<MetricsProvider>
        get() = setOf(MetricsProvider.BSTATS)

    /**
     * Opens and owns one metrics session for a native plugin [owner].
     *
     * @param projectId positive bStats project identifier assigned to that plugin
     * @throws IllegalArgumentException when [projectId] is not positive
     */
    fun open(owner: Any, projectId: Int): PluginMetrics

    /** Opens one or more configured provider sessions through a neutral request object. */
    fun open(owner: Any, configurations: Collection<MetricsProviderConfiguration>): PluginMetrics {
        val configuration = configurations.firstOrNull { it.enabled }
            ?: error("At least one metrics provider must be enabled")
        return open(owner, requireNotNull(configuration.projectId) {
            "Legacy MetricsService requires a numeric projectId for ${configuration.provider}"
        })
    }

    /** Opens the optional provider-backed error reporter for the same configuration. */
    fun openErrorReporter(owner: Any, configurations: Collection<MetricsProviderConfiguration>): ErrorReporter? = null
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
    /** Starts provider submission after all configured charts and services are registered. */
    fun start() = Unit

    /** Provider that owns this session. Composite sessions expose [MetricsProvider.BSTATS]. */
    val provider: MetricsProvider
        get() = MetricsProvider.BSTATS

    /** Providers represented by this session; a composite may contain more than one. */
    val providers: Set<MetricsProvider>
        get() = setOf(provider)

    /** Provider capabilities available for this session. */
    val capabilities: Set<MetricsCapability>
        get() = setOf(MetricsCapability.CHARTS)

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
