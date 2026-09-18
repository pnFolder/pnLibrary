package ru.privatenull.pnlibrary.core.metrics

import org.bstats.charts.AdvancedBarChart
import org.bstats.charts.AdvancedPie
import org.bstats.charts.CustomChart
import org.bstats.charts.DrilldownPie
import org.bstats.charts.MultiLineChart
import org.bstats.charts.SimpleBarChart
import org.bstats.charts.SimplePie
import org.bstats.charts.SingleLineChart
import ru.privatenull.pnlibrary.api.metrics.PluginMetrics
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Supplier

/** Shared chart mapping; platform modules only construct their native Metrics object. */
class BStatsMetricsSession(
    /** Numeric bStats project receiving this session's charts. */
    override val projectId: Int,
    private val addChart: (CustomChart) -> Unit,
    private val shutdown: () -> Unit,
) : PluginMetrics {
    private val closed = AtomicBoolean(false)

    /** Validates [id] and registers the bStats equivalent of [PluginMetrics.simplePie]. */
    override fun simplePie(id: String, value: Supplier<String?>) = add(SimplePie(valid(id)) { value.get() })
    /** Validates [id] and registers the bStats equivalent of [PluginMetrics.advancedPie]. */
    override fun advancedPie(id: String, values: Supplier<Map<String, Int>>) = add(AdvancedPie(valid(id)) { values.get() })
    /** Validates [id] and registers the bStats equivalent of [PluginMetrics.drilldownPie]. */
    override fun drilldownPie(id: String, values: Supplier<Map<String, Map<String, Int>>>) = add(DrilldownPie(valid(id)) { values.get() })
    /** Validates [id] and registers the bStats equivalent of [PluginMetrics.singleLineChart]. */
    override fun singleLineChart(id: String, value: Supplier<Int>) = add(SingleLineChart(valid(id)) { value.get() })
    /** Validates [id] and registers the bStats equivalent of [PluginMetrics.multiLineChart]. */
    override fun multiLineChart(id: String, values: Supplier<Map<String, Int>>) = add(MultiLineChart(valid(id)) { values.get() })
    /** Validates [id] and registers the bStats equivalent of [PluginMetrics.simpleBarChart]. */
    override fun simpleBarChart(id: String, values: Supplier<Map<String, Int>>) = add(SimpleBarChart(valid(id)) { values.get() })
    /** Validates [id] and registers the bStats equivalent of [PluginMetrics.advancedBarChart]. */
    override fun advancedBarChart(id: String, values: Supplier<Map<String, IntArray>>) = add(AdvancedBarChart(valid(id)) { values.get() })

    private fun add(chart: CustomChart): PluginMetrics {
        check(!closed.get()) { "Metrics session is closed" }
        addChart(chart)
        return this
    }

    /** Shuts down the native bStats session exactly once. */
    override fun close() {
        if (closed.compareAndSet(false, true)) shutdown()
    }

    private fun valid(id: String): String {
        val normalized = id.trim()
        require(normalized.matches(Regex("[A-Za-z0-9_-]{1,64}"))) { "Invalid bStats chart id: $id" }
        return normalized
    }
}
