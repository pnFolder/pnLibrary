package org.pnfolder.pnlibrary.core.plugin

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.metrics.ErrorReporter
import org.pnfolder.pnlibrary.api.metrics.MetricsProvider
import org.pnfolder.pnlibrary.api.metrics.MetricsProviderConfiguration
import org.pnfolder.pnlibrary.api.metrics.MetricsService
import org.pnfolder.pnlibrary.api.metrics.PluginMetrics
import org.pnfolder.pnlibrary.api.metrics.TelemetryError
import org.pnfolder.pnlibrary.core.plugin.module.ModuleMetricsDefinition
import java.util.function.Consumer
import java.util.function.Supplier

class MetricsControllerImplTest {
    @Test
    fun `adding bStats to a FastStats only configuration opens both providers`() {
        val service = RecordingMetricsService()
        val controller = MetricsControllerImpl(
            Any(), service,
            ModuleMetricsDefinition(providers = listOf(fastStats()), enabled = false),
        )

        controller.update(bStats(10))
        controller.enable()

        assertEquals(
            setOf(MetricsProvider.BSTATS, MetricsProvider.FASTSTATS),
            service.opened.single().providers,
        )
        controller.close()
    }

    @Test
    fun `error reporter follows enabled session lifecycle`() {
        val reporter = object : ErrorReporter {
            override fun capture(error: TelemetryError) = Unit
            override fun close() = Unit
        }
        val service = RecordingMetricsService(reporter)
        val controller = MetricsControllerImpl(Any(), service, definition(), null)
        assertEquals(reporter, controller.errorReporterOrNull())
        controller.disable()
        assertEquals(null, controller.errorReporterOrNull())
        controller.enable()
        assertEquals(reporter, controller.errorReporterOrNull())
        controller.close()
        assertEquals(null, controller.errorReporterOrNull())
    }

    @Test
    fun `runtime chart configuration restarts session and replays all charts`() {
        val service = RecordingMetricsService()
        val controller = MetricsControllerImpl(Any(), service, definition(), null)

        controller.configure(Consumer { it.simplePie("mode", Supplier { "test" }) })
        controller.configure(Consumer { it.simplePie("database", Supplier { "mysql" }) })

        assertTrue(service.opened.first().closed)
        assertEquals(listOf("mode", "database"), service.opened.last().charts)
        controller.disable()
        assertEquals(null, controller.errorReporterOrNull())
        controller.close()
    }

    @Test
    fun `metrics can be disabled enabled and moved to another provider setup`() {
        val service = RecordingMetricsService()
        val controller = MetricsControllerImpl(
            owner = Any(),
            service = service,
            definition = definition(
                configurers = listOf(Consumer { it.simplePie("mode", Supplier { "test" }) }),
            ),
        )

        assertTrue(controller.isEnabled)
        assertEquals(listOf(listOf(10)), service.requestedProjectIds())
        assertEquals(listOf("mode"), service.opened.single().charts)

        controller.disable()
        assertFalse(controller.isEnabled)
        assertTrue(service.opened.single().closed)
        assertEquals(listOf(bStats(10)), controller.providerConfigurations)

        controller.enable()
        assertTrue(controller.isEnabled)
        assertEquals(listOf(listOf(10), listOf(10)), service.requestedProjectIds())

        controller.update(bStats(20))
        assertEquals(listOf(bStats(20)), controller.providerConfigurations)
        assertTrue(controller.isEnabled)
        assertEquals(listOf(listOf(10), listOf(10), listOf(20)), service.requestedProjectIds())
        assertTrue(service.opened[1].closed)
        assertEquals(listOf("mode"), service.opened[2].charts)
    }

    @Test
    fun `unchanged provider configuration does not restart the session`() {
        val service = RecordingMetricsService()
        val controller = MetricsControllerImpl(Any(), service, definition(), null)

        controller.update(bStats(10))

        assertEquals(1, service.opened.size)
        assertFalse(service.opened.single().closed)
    }

    @Test
    fun `enabling without an enabled provider is rejected`() {
        val controller = MetricsControllerImpl(
            Any(), RecordingMetricsService(),
            ModuleMetricsDefinition(enabled = false),
        )

        assertThrows(IllegalArgumentException::class.java) { controller.enable() }
        assertFalse(controller.isEnabled)
    }

    @Test
    fun `closed controller rejects mutation`() {
        val controller = MetricsControllerImpl(
            Any(),
            RecordingMetricsService(),
            definition(enabled = false),
            null,
        )
        controller.close()

        assertTrue(controller.isClosed)
        assertThrows(IllegalStateException::class.java) { controller.enable() }
        assertThrows(IllegalStateException::class.java) { controller.update(bStats(20)) }
    }

    private class RecordingMetricsService(private val reporter: ErrorReporter? = null) : MetricsService {
        val opened = mutableListOf<RecordingMetrics>()
        private val requests = mutableListOf<List<MetricsProviderConfiguration>>()

        override fun open(owner: Any, configurations: Collection<MetricsProviderConfiguration>): PluginMetrics {
            requests += configurations.toList()
            return RecordingMetrics(configurations.map { it.provider }.toSet(), reporter).also { opened += it }
        }

        fun requestedProjectIds(): List<List<Int>> = requests.map { request -> request.mapNotNull { it.projectId } }
    }

    companion object {
        private fun bStats(projectId: Int) = MetricsProviderConfiguration(MetricsProvider.BSTATS, projectId = projectId)
        private fun fastStats() = MetricsProviderConfiguration(MetricsProvider.FASTSTATS, token = "test-token")

        private fun definition(
            enabled: Boolean = true,
            configurers: List<Consumer<PluginMetrics>> = emptyList(),
        ) = ModuleMetricsDefinition(
            providers = listOf(bStats(10)),
            enabled = enabled,
            configurers = configurers,
        )
    }

    private class RecordingMetrics(
        override val providers: Set<MetricsProvider>,
        override val errorReporter: ErrorReporter? = null,
    ) : PluginMetrics {
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
