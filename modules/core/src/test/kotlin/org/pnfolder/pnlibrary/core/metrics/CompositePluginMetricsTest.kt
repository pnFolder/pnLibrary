package org.pnfolder.pnlibrary.core.metrics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.metrics.ErrorReporter
import org.pnfolder.pnlibrary.api.metrics.MetricsProvider
import org.pnfolder.pnlibrary.api.metrics.PluginMetrics
import org.pnfolder.pnlibrary.api.metrics.TelemetryError
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Supplier

class CompositePluginMetricsTest {
    @Test
    fun `caller cannot remove owned backends or mutate provider metadata`() {
        val calls = mutableListOf<String>()
        val source = mutableListOf<PluginMetrics>(
            RecordingMetrics(MetricsProvider.BSTATS, calls),
            RecordingMetrics(MetricsProvider.FASTSTATS, calls),
        )
        val composite = CompositePluginMetrics(source)
        source.clear()
        assertThrows(UnsupportedOperationException::class.java) {
            (composite.providers as MutableSet<MetricsProvider>).clear()
        }
        composite.close()
        assertEquals(listOf("faststats:close", "bstats:close"), calls)
    }

    @Test
    fun `close releases every backend even when one backend fails`() {
        val calls = mutableListOf<String>()
        val first = RecordingMetrics(MetricsProvider.BSTATS, calls)
        val second = object : PluginMetrics by RecordingMetrics(MetricsProvider.FASTSTATS, calls) {
            override fun close() {
                calls += "faststats:close"
                error("shutdown failed")
            }
        }
        val composite = CompositePluginMetrics(listOf(first, second))
        assertThrows(IllegalStateException::class.java) { composite.close() }
        assertEquals(listOf("faststats:close", "bstats:close"), calls)
        composite.close()
        assertEquals(listOf("faststats:close", "bstats:close"), calls)
    }

    @Test
    fun `composite exposes the error reporter owned by its provider`() {
        val reporter = object : ErrorReporter {
            override fun capture(error: TelemetryError) = Unit
            override fun close() = Unit
        }
        val charts = RecordingMetrics(MetricsProvider.BSTATS, mutableListOf())
        val errors = object : PluginMetrics by charts {
            override val errorReporter = reporter
        }
        assertEquals(reporter, CompositePluginMetrics(listOf(charts, errors)).errorReporter)
        assertEquals(null, CompositePluginMetrics(listOf(charts)).errorReporter)
    }

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
    }

    private class RecordingMetrics(
        private val provider: MetricsProvider,
        private val calls: MutableList<String>,
    ) : PluginMetrics {
        private val chartCount = AtomicInteger()

        override val providers: Set<MetricsProvider> = setOf(provider)
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
