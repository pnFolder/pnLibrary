package ru.privatenull.pnlibrary.core.plugin

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.metrics.MetricsService
import ru.privatenull.pnlibrary.api.metrics.PluginMetrics
import java.util.function.Consumer
import java.util.function.Supplier

class MetricsControllerImplTest {
    @Test
    fun `FastStats facade follows enabled session lifecycle`() {
        val reporter = object : ru.privatenull.pnlibrary.api.metrics.ErrorReporter {
            override fun capture(error: ru.privatenull.pnlibrary.api.metrics.TelemetryError) = Unit
            override fun close() = Unit
        }
        val service = object : MetricsService {
            override fun open(owner: Any, projectId: Int): PluginMetrics =
                RecordingMetrics(projectId, reporter)
        }
        val controller = MetricsControllerImpl(Any(), service, 10, true, emptyList())
        assertEquals(reporter, controller.fastStatsOrNull()?.errorTracker())
        controller.disable()
        assertEquals(null, controller.fastStatsOrNull())
        controller.enable()
        assertEquals(reporter, controller.fastStatsOrNull()?.errorTracker())
        controller.close()
        assertEquals(null, controller.fastStatsOrNull())
    }

    @Test
    fun `runtime chart configuration restarts session and replays all charts`() {
        val service = RecordingMetricsService()
        val controller = MetricsControllerImpl(Any(), service, 10, true, emptyList())

        controller.configure(Consumer { it.simplePie("mode", Supplier { "test" }) })
        controller.configure(Consumer { it.simplePie("database", Supplier { "mysql" }) })

        assertTrue(service.opened.first().closed)
        assertEquals(listOf("mode", "database"), service.opened.last().charts)
        controller.disable()
        assertEquals(null, controller.errorReporterOrNull())
        controller.close()
    }

    @Test
    fun `metrics can be disabled enabled and moved to another project`() {
        val service = RecordingMetricsService()
        val controller = MetricsControllerImpl(
            owner = Any(),
            service = service,
            initialProjectId = 10,
            initiallyEnabled = true,
            initialConfigurers = listOf(Consumer { it.simplePie("mode", Supplier { "test" }) }),
        )

        assertTrue(controller.isEnabled)
        assertEquals(listOf(10), service.opened.map { it.projectId })
        assertEquals(listOf("mode"), service.opened.single().charts)

        controller.disable()
        assertFalse(controller.isEnabled)
        assertTrue(service.opened.single().closed)
        assertEquals(10, controller.projectId)

        controller.enable()
        assertTrue(controller.isEnabled)
        assertEquals(listOf(10, 10), service.opened.map { it.projectId })

        controller.changeProjectId(20)
        assertEquals(20, controller.projectId)
        assertTrue(controller.isEnabled)
        assertEquals(listOf(10, 10, 20), service.opened.map { it.projectId })
        assertTrue(service.opened[1].closed)
        assertEquals(listOf("mode"), service.opened[2].charts)
    }

    @Test
    fun `closed controller rejects mutation`() {
        val controller = MetricsControllerImpl(Any(), RecordingMetricsService(), 10, false, emptyList())
        controller.close()

        assertTrue(controller.isClosed)
        assertThrows(IllegalStateException::class.java) { controller.enable() }
        assertThrows(IllegalStateException::class.java) { controller.changeProjectId(20) }
    }

    private class RecordingMetricsService : MetricsService {
        val opened = mutableListOf<RecordingMetrics>()
        override fun open(owner: Any, projectId: Int): PluginMetrics =
            RecordingMetrics(projectId).also { opened += it }
    }

    private class RecordingMetrics(
        override val projectId: Int,
        override val errorReporter: ru.privatenull.pnlibrary.api.metrics.ErrorReporter? = null,
    ) : PluginMetrics {
        override val provider get() = if (errorReporter == null)
            ru.privatenull.pnlibrary.api.metrics.MetricsProvider.BSTATS
        else ru.privatenull.pnlibrary.api.metrics.MetricsProvider.FASTSTATS
        val charts = mutableListOf<String>()
        var closed = false
        override fun simplePie(id: String, value: Supplier<String?>) = apply { charts += id }
        override fun advancedPie(id: String, values: Supplier<Map<String, Int>>) = this
        override fun drilldownPie(id: String, values: Supplier<Map<String, Map<String, Int>>>) = this
        override fun singleLineChart(id: String, value: Supplier<Int>) = this
        override fun multiLineChart(id: String, values: Supplier<Map<String, Int>>) = this
        override fun simpleBarChart(id: String, values: Supplier<Map<String, Int>>) = this
        override fun advancedBarChart(id: String, values: Supplier<Map<String, IntArray>>) = this
        override fun close() { closed = true }
    }
}
