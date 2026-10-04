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
    override fun fastStatsOrNull() = delegates.firstNotNullOfOrNull { it.fastStatsOrNull() }
    override val errorReporter
        get() = delegates.firstNotNullOfOrNull { it.errorReporter }
    init {
        require(delegates.isNotEmpty()) { "At least one metrics delegate is required" }
    }

    override val projectId: Int
        get() = delegates.first().projectId

    override val provider: MetricsProvider
        get() = delegates.first().provider

    override val providers: Set<MetricsProvider> = delegates
        .flatMap { it.providers }
        .toSet()

    override val capabilities: Set<MetricsCapability> = delegates
        .flatMap { it.capabilities }
        .toSet()

    override fun simplePie(id: String, value: Supplier<String?>): PluginMetrics =
        fanOut { it.simplePie(id, value) }

    override fun advancedPie(id: String, values: Supplier<Map<String, Int>>): PluginMetrics =
        fanOut { it.advancedPie(id, values) }

    override fun drilldownPie(
        id: String,
        values: Supplier<Map<String, Map<String, Int>>>,
    ): PluginMetrics = fanOut { it.drilldownPie(id, values) }

    override fun singleLineChart(id: String, value: Supplier<Int>): PluginMetrics =
        fanOut { it.singleLineChart(id, value) }

    override fun multiLineChart(id: String, values: Supplier<Map<String, Int>>): PluginMetrics =
        fanOut { it.multiLineChart(id, values) }

    override fun simpleBarChart(id: String, values: Supplier<Map<String, Int>>): PluginMetrics =
        fanOut { it.simpleBarChart(id, values) }

    override fun advancedBarChart(id: String, values: Supplier<Map<String, IntArray>>): PluginMetrics =
        fanOut { it.advancedBarChart(id, values) }

    override fun close() {
        try {
            delegates.asReversed().forEach { it.close() }
        } finally {
            onClose()
        }
    }

    override fun start() {
        delegates.forEach { it.start() }
    }

    private fun fanOut(action: (PluginMetrics) -> Any?): PluginMetrics {
        delegates.forEach { delegate -> action(delegate) }
        return this
    }
}
