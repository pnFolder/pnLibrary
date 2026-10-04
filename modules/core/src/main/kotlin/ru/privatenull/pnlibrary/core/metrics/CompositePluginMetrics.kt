package ru.privatenull.pnlibrary.core.metrics

import ru.privatenull.pnlibrary.api.metrics.MetricsCapability
import ru.privatenull.pnlibrary.api.metrics.MetricsProvider
import ru.privatenull.pnlibrary.api.metrics.PluginMetrics
import java.util.function.Supplier

/**
 * Fan-out session used when more than one metrics provider is enabled.
 *
 * The shared chart contract is registered with every delegate. Provider-specific
 * extensions remain behind the provider's own adapter and are not silently reduced
 * to a chart that another provider cannot represent.
 */
class CompositePluginMetrics(
    private val delegates: List<PluginMetrics>,
    private val onClose: () -> Unit = {},
) : PluginMetrics {
    /** Returns the first FastStats capability exposed by a delegate. */
    override fun fastStatsOrNull() = delegates.firstNotNullOfOrNull { it.fastStatsOrNull() }
    /** Returns the first error reporter exposed by a delegate. */
    override val errorReporter
        get() = delegates.firstNotNullOfOrNull { it.errorReporter }
    init {
        require(delegates.isNotEmpty()) { "At least one metrics delegate is required" }
    }

    /** Compatibility project ID exposed by the first delegate. */
    override val projectId: Int
        get() = delegates.first().projectId

    /** Compatibility provider exposed by the first delegate. */
    override val provider: MetricsProvider
        get() = delegates.first().provider

    /** Union of every represented metrics provider. */
    override val providers: Set<MetricsProvider> = delegates
        .flatMap { it.providers }
        .toSet()

    /** Union of every delegate capability. */
    override val capabilities: Set<MetricsCapability> = delegates
        .flatMap { it.capabilities }
        .toSet()

    /** Registers a simple pie chart with every delegate. */
    override fun simplePie(id: String, value: Supplier<String?>): PluginMetrics =
        fanOut { it.simplePie(id, value) }

    /** Registers an advanced pie chart with every delegate. */
    override fun advancedPie(id: String, values: Supplier<Map<String, Int>>): PluginMetrics =
        fanOut { it.advancedPie(id, values) }

    /** Registers a drilldown pie chart with every delegate. */
    override fun drilldownPie(
        id: String,
        values: Supplier<Map<String, Map<String, Int>>>,
    ): PluginMetrics = fanOut { it.drilldownPie(id, values) }

    /** Registers a single-line chart with every delegate. */
    override fun singleLineChart(id: String, value: Supplier<Int>): PluginMetrics =
        fanOut { it.singleLineChart(id, value) }

    /** Registers a multiline chart with every delegate. */
    override fun multiLineChart(id: String, values: Supplier<Map<String, Int>>): PluginMetrics =
        fanOut { it.multiLineChart(id, values) }

    /** Registers a simple bar chart with every delegate. */
    override fun simpleBarChart(id: String, values: Supplier<Map<String, Int>>): PluginMetrics =
        fanOut { it.simpleBarChart(id, values) }

    /** Registers an advanced bar chart with every delegate. */
    override fun advancedBarChart(id: String, values: Supplier<Map<String, IntArray>>): PluginMetrics =
        fanOut { it.advancedBarChart(id, values) }

    /** Closes delegates in reverse order and invokes the ownership callback. */
    override fun close() {
        try {
            delegates.asReversed().forEach { it.close() }
        } finally {
            onClose()
        }
    }

    /** Starts submission for every delegate. */
    override fun start() {
        delegates.forEach { it.start() }
    }

    private fun fanOut(action: (PluginMetrics) -> Any?): PluginMetrics {
        delegates.forEach { delegate -> action(delegate) }
        return this
    }
}
