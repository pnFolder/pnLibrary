package ru.privatenull.pnlibrary.core.metrics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.metrics.MetricsCapability
import ru.privatenull.pnlibrary.api.metrics.MetricsProvider
import ru.privatenull.pnlibrary.api.metrics.PluginMetrics
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Supplier

class CompositePluginMetricsTest {
    @Test
    fun `shared charts fan out to every provider and close in reverse order`() {
        val calls = mutableListOf<String>()
        val first = RecordingMetrics(MetricsProvider.BSTATS, calls)
        val second = RecordingMetrics(MetricsProvider.FASTSTATS, calls)
        val composite = CompositePluginMetrics(listOf(first, second))

        composite.simplePie("platform", Supplier { "Paper" })
        composite.close()

        assertEquals(setOf(MetricsProvider.BSTATS, MetricsProvider.FASTSTATS), composite.providers)
        assertEquals(listOf("bstats:chart", "faststats:chart", "faststats:close", "bstats:close"), calls)
        assertTrue(composite.capabilities.contains(MetricsCapability.CHARTS))
    }

    private class RecordingMetrics(
        override val provider: MetricsProvider,
        private val calls: MutableList<String>,
    ) : PluginMetrics {
        private val chartCount = AtomicInteger()

        override val projectId: Int = 1
        override fun simplePie(id: String, value: Supplier<String?>): PluginMetrics {
            chartCount.incrementAndGet()
            calls += "${provider.name.lowercase()}:chart"
            return this
        }

        override fun advancedPie(id: String, values: Supplier<Map<String, Int>>) = this
        override fun drilldownPie(id: String, values: Supplier<Map<String, Map<String, Int>>>) = this
        override fun singleLineChart(id: String, value: Supplier<Int>) = this
        override fun multiLineChart(id: String, values: Supplier<Map<String, Int>>) = this
        override fun simpleBarChart(id: String, values: Supplier<Map<String, Int>>) = this
        override fun advancedBarChart(id: String, values: Supplier<Map<String, IntArray>>) = this

        override fun close() {
            calls += "${provider.name.lowercase()}:close"
        }
    }
}
