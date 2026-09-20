package ru.privatenull.pnlibrary.core

import ru.privatenull.pnlibrary.core.metrics.MetricsRegistry


import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.diagnostics.*
import ru.privatenull.pnlibrary.api.logging.*
import ru.privatenull.pnlibrary.api.metrics.*
import ru.privatenull.pnlibrary.spi.metrics.PlatformMetricsFactory
import ru.privatenull.pnlibrary.api.runtime.*
import ru.privatenull.pnlibrary.api.tasks.*
import ru.privatenull.pnlibrary.api.updates.*
import java.util.function.Supplier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class MetricsRegistryTest {
    @Test
    fun `project id is required and runtime closes sessions`() {
        var closed = 0
        val factory = object : PlatformMetricsFactory {
            override fun open(owner: Any, projectId: Int): PluginMetrics = FakeMetrics(projectId) { closed++ }
        }
        val registry = MetricsRegistry(factory)

        assertThrows(IllegalArgumentException::class.java) { registry.open(Any(), 0) }
        val first = registry.open(Any(), 123)
        registry.open(Any(), 456)
        first.simplePie("storage_type") { "sqlite" }
        first.close()
        registry.close()

        assertEquals(2, closed)
    }

    @Test
    fun `managed metrics closes delegate only once under concurrent close`() {
        val closed = AtomicInteger()
        val registry = MetricsRegistry(object : PlatformMetricsFactory {
            override fun open(owner: Any, projectId: Int): PluginMetrics =
                FakeMetrics(projectId) { closed.incrementAndGet() }
        })
        val metrics = registry.open(Any(), 123)
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

    private class FakeMetrics(override val projectId: Int, val shutdown: () -> Unit) : PluginMetrics {
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
