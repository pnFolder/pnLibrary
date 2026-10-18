package ru.privatenull.pnlibrary.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.metrics.ErrorReporter
import ru.privatenull.pnlibrary.api.metrics.MetricsProvider
import ru.privatenull.pnlibrary.api.metrics.MetricsProviderConfiguration
import ru.privatenull.pnlibrary.api.metrics.PluginMetrics
import ru.privatenull.pnlibrary.core.metrics.MetricsServiceImpl
import ru.privatenull.pnlibrary.spi.metrics.PlatformMetricsFactory
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Supplier

class MetricsServiceImplTest {
    private val bStats = listOf(MetricsProviderConfiguration(MetricsProvider.BSTATS, projectId = 123))
    private val fastStats = listOf(MetricsProviderConfiguration(MetricsProvider.FASTSTATS, token = "token"))

    @Test
    fun `closed registry rejects every metrics factory entry point`() {
        var opened = 0
        val registry = MetricsServiceImpl(object : PlatformMetricsFactory {
            override fun open(owner: Any, configurations: Collection<MetricsProviderConfiguration>): PluginMetrics {
                opened++
                return FakeMetrics {}
            }

            override fun openErrorReporter(
                owner: Any,
                configurations: Collection<MetricsProviderConfiguration>,
            ): ErrorReporter? {
                opened++
                return null
            }
        })
        registry.close()
        assertThrows(IllegalStateException::class.java) { registry.open(Any(), bStats) }
        assertThrows(IllegalStateException::class.java) { registry.open(Any(), fastStats) }
        assertThrows(IllegalStateException::class.java) { registry.openErrorReporter(Any(), fastStats) }
        assertEquals(0, opened)
        registry.close()
    }

    @Test
    fun `an enabled provider is required and runtime closes sessions`() {
        var closed = 0
        val registry = MetricsServiceImpl(object : PlatformMetricsFactory {
            override fun open(owner: Any, configurations: Collection<MetricsProviderConfiguration>): PluginMetrics =
                FakeMetrics { closed++ }
        })

        assertThrows(IllegalArgumentException::class.java) { registry.open(Any(), emptyList()) }
        assertThrows(IllegalArgumentException::class.java) {
            registry.open(Any(), bStats.map { it.copy(enabled = false) })
        }
        val first = registry.open(Any(), bStats)
        registry.open(Any(), fastStats)
        first.simplePie("storage_type") { "sqlite" }
        first.close()
        registry.close()

        assertEquals(2, closed)
    }

    @Test
    fun `managed metrics closes delegate only once under concurrent close`() {
        val closed = AtomicInteger()
        val registry = MetricsServiceImpl(object : PlatformMetricsFactory {
            override fun open(owner: Any, configurations: Collection<MetricsProviderConfiguration>): PluginMetrics =
                FakeMetrics { closed.incrementAndGet() }
        })
        val metrics = registry.open(Any(), bStats)
        val executor = Executors.newFixedThreadPool(8)
        try {
            repeat(64) { executor.submit { metrics.close() } }
        } finally {
            executor.shutdown()
            check(executor.awaitTermination(5, TimeUnit.SECONDS))
        }

        registry.close()
        assertEquals(1, closed.get())
    }

    @Test
    fun `provider configuration is delegated and managed`() {
        var received: List<MetricsProviderConfiguration> = emptyList()
        var closed = 0
        val registry = MetricsServiceImpl(object : PlatformMetricsFactory {
            override fun open(owner: Any, configurations: Collection<MetricsProviderConfiguration>): PluginMetrics {
                received = configurations.toList()
                return FakeMetrics { closed++ }
            }
        })

        registry.open(Any(), fastStats)
        registry.close()

        assertEquals(fastStats, received)
        assertEquals(1, closed)
    }

    private class FakeMetrics(val shutdown: () -> Unit) : PluginMetrics {
        override val providers: Set<MetricsProvider> = setOf(MetricsProvider.BSTATS)
        override fun simplePie(id: String, value: Supplier<String?>) = this
        override fun advancedPie(id: String, values: Supplier<Map<String, Int>>) = this
        override fun drilldownPie(id: String, values: Supplier<Map<String, Map<String, Int>>>) = this
        override fun singleLineChart(id: String, value: Supplier<Int>) = this
        override fun multiLineChart(id: String, values: Supplier<Map<String, Int>>) = this
        override fun simpleBarChart(id: String, values: Supplier<Map<String, Int>>) = this
        override fun advancedBarChart(id: String, values: Supplier<Map<String, IntArray>>) = this
        override fun close() = shutdown()
    }
}
