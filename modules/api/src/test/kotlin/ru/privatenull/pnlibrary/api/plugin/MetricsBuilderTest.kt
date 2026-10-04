package ru.privatenull.pnlibrary.api.plugin

import ru.privatenull.pnlibrary.api.metrics.MetricsProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class MetricsBuilderTest {
    @Test
    fun `builds independent bStats and FastStats configurations`() {
        val configurations = MetricsBuilder().apply {
            bStats(32592)
            fastStats("token")
        }.build()

        assertEquals(
            listOf(MetricsProvider.BSTATS, MetricsProvider.FASTSTATS),
            configurations.map { it.provider },
        )
        assertEquals(32592, configurations[0].projectId)
        assertEquals("token", configurations[1].token)
    }

    @Test
    fun `rejects empty metrics configuration`() {
        assertThrows(IllegalArgumentException::class.java) { MetricsBuilder().build() }
    }

    @Test
    fun `keeps initial state and charts inside the metrics DSL`() {
        var configured = false
        val builder = MetricsBuilder()
            .bStats(32592)
            .enabled(false)
            .charts { configured = true }

        assertFalse(builder.enabled)
        builder.configure(object : ru.privatenull.pnlibrary.api.metrics.PluginMetrics {
            override val projectId = 32592
            override fun simplePie(id: String, value: java.util.function.Supplier<String?>) = this
            override fun advancedPie(id: String, values: java.util.function.Supplier<Map<String, Int>>) = this
            override fun drilldownPie(id: String, values: java.util.function.Supplier<Map<String, Map<String, Int>>>) = this
            override fun singleLineChart(id: String, value: java.util.function.Supplier<Int>) = this
            override fun multiLineChart(id: String, values: java.util.function.Supplier<Map<String, Int>>) = this
            override fun simpleBarChart(id: String, values: java.util.function.Supplier<Map<String, Int>>) = this
            override fun advancedBarChart(id: String, values: java.util.function.Supplier<Map<String, IntArray>>) = this
            override fun close() = Unit
        })
        assertEquals(true, configured)
    }
}
