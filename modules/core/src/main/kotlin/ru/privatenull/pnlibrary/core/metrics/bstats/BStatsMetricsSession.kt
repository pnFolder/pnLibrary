package ru.privatenull.pnlibrary.core.metrics.bstats

import org.bstats.charts.AdvancedBarChart
import org.bstats.charts.AdvancedPie
import org.bstats.charts.CustomChart
import org.bstats.charts.DrilldownPie
import org.bstats.charts.MultiLineChart
import org.bstats.charts.SimpleBarChart
import org.bstats.charts.SimplePie
import org.bstats.charts.SingleLineChart
import ru.privatenull.pnlibrary.core.metrics.AbstractMetricsSession

/** Translates shared lazy chart definitions into native bStats charts. */
class BStatsMetricsSession(
    projectId: Int,
    private val addChart: (CustomChart) -> Unit,
    private val shutdown: () -> Unit,
) : AbstractMetricsSession(projectId) {

    /** Registers the matching native chart without evaluating its supplier. */
    override fun register(chart: Chart) {
        val native = when (chart) {
            is Chart.SimplePie -> SimplePie(chart.id) { chart.value.get() }
            is Chart.AdvancedPie -> AdvancedPie(chart.id) { chart.values.get() }
            is Chart.DrilldownPie -> DrilldownPie(chart.id) { chart.values.get() }
            is Chart.SingleLine -> SingleLineChart(chart.id) { chart.value.get() }
            is Chart.MultiLine -> MultiLineChart(chart.id) { chart.values.get() }
            is Chart.SimpleBar -> SimpleBarChart(chart.id) { chart.values.get() }
            is Chart.AdvancedBar -> AdvancedBarChart(chart.id) { chart.values.get() }
        }
        addChart(native)
    }

    /** Stops the native bStats submission service. */
    override fun shutdown() {
        shutdown.invoke()
    }
}
