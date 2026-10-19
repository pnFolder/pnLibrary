package org.pnfolder.pnlibrary.core.metrics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.metrics.MetricsCredentials
import java.util.function.Supplier

class AbstractMetricsSessionTest {

    @Test
    fun `charts are normalized and suppliers remain lazy`() {
        val session = RecordingSession()
        var reads = 0
        assertSame(session, session.simplePie(" mode ", Supplier { reads++; "paper" }))
        assertEquals(0, reads)
        val chart = session.charts.single() as AbstractMetricsSession.Chart.SimplePie
        assertEquals("mode", chart.id)
        assertEquals("paper", chart.value.get())
        assertEquals(1, reads)
    }

    @Test
    fun `all chart kinds retain their distinct types`() {
        val session = RecordingSession()
        session.simplePie("pie", Supplier { "a" })
        session.advancedPie("advanced", Supplier { mapOf("a" to 1) })
        session.drilldownPie("drill", Supplier { mapOf("a" to mapOf("b" to 2)) })
        session.singleLineChart("line", Supplier { 3 })
        session.multiLineChart("lines", Supplier { mapOf("c" to 4) })
        session.simpleBarChart("bar", Supplier { mapOf("d" to 5) })
        session.advancedBarChart("bars", Supplier { mapOf("e" to intArrayOf(6)) })
        assertEquals(
            listOf("SimplePie", "AdvancedPie", "DrilldownPie", "SingleLine", "MultiLine", "SimpleBar", "AdvancedBar"),
            session.charts.map { it.javaClass.simpleName },
        )
    }

    @Test
    fun `invalid identifiers never reach a provider`() {
        val session = RecordingSession()
        for (id in listOf("", "bad id", "x".repeat(65))) {
            assertThrows(IllegalArgumentException::class.java) {
                session.singleLineChart(id, Supplier { 1 })
            }
        }
        assertEquals(0, session.charts.size)
    }

    @Test
    fun `close shuts down once and prevents registration`() {
        val session = RecordingSession()
        session.close()
        session.close()
        assertEquals(1, session.shutdowns)
        assertThrows(IllegalStateException::class.java) {
            session.singleLineChart("line", Supplier { 1 })
        }
    }

    @Test
    fun `shutdown failure still closes owned credentials`() {
        val credentials = MetricsCredentials.FastStats("private".toCharArray())
        val session = object : AbstractMetricsSession(credentials) {
            override fun register(chart: Chart) = Unit
            override fun shutdown() { throw IllegalStateException("native shutdown failed") }
        }
        assertThrows(IllegalStateException::class.java) { session.close() }
        assertThrows(IllegalStateException::class.java) { credentials.useToken { it } }
    }

    private class RecordingSession : AbstractMetricsSession(MetricsCredentials.BStats(42)) {
        val charts = mutableListOf<Chart>()
        var shutdowns = 0

        override fun register(chart: Chart) {
            charts += chart
        }

        override fun shutdown() {
            shutdowns++
        }
    }
}
