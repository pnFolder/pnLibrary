package org.pnfolder.pnlibrary.core.metrics

import org.pnfolder.pnlibrary.api.metrics.MetricsProvider
import org.pnfolder.pnlibrary.api.metrics.PluginMetrics
import org.pnfolder.pnlibrary.core.lifecycle.ResourceCleanup
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Supplier

/**
 * Fan-out session used when more than one metrics provider is enabled.
 *
 * The shared chart contract is registered with every delegate. Provider-specific
 * extensions remain behind the provider's own adapter and are not silently reduced
 * to a chart that another provider cannot represent.
 */
internal class CompositePluginMetrics(
    delegates: List<PluginMetrics>,
    private val onClose: () -> Unit = {},
) : PluginMetrics {
    private val delegates = delegates.toList()
    private val closed = AtomicBoolean(false)

    init {
        require(this.delegates.isNotEmpty()) { "At least one metrics delegate is required" }
    }

    /** Returns the first error reporter exposed by a delegate. */
    override val errorReporter
        get() = delegates.firstNotNullOfOrNull { it.errorReporter }

    /** Union of every represented metrics provider. */
    override val providers: Set<MetricsProvider> = Collections.unmodifiableSet(this.delegates
        .flatMap { it.providers }
        .toSet())

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

    /** Closes delegates in reverse order and invokes the ownership callback. */
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        ResourceCleanup.closeAll(
            *delegates.asReversed().map { delegate -> ({ delegate.close() }) }.toTypedArray(),
            onClose,
        )
    }

    override fun start() {
        delegates.forEach { it.start() }
    }

    private fun fanOut(action: (PluginMetrics) -> Any?): PluginMetrics {
        delegates.forEach { delegate -> action(delegate) }
        return this
    }
}
