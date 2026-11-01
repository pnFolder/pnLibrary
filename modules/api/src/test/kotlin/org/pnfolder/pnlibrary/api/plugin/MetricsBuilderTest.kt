package org.pnfolder.pnlibrary.api.plugin

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.metrics.MetricsBuilder
import org.pnfolder.pnlibrary.api.metrics.MetricsProvider
import org.pnfolder.pnlibrary.api.metrics.PluginMetrics
import org.pnfolder.pnlibrary.api.platform.PlatformType
import java.util.function.Supplier

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
    fun `picks the bStats project of the running platform`() {
        val configuration = MetricsBuilder()
            .bStats(PlatformType.BUKKIT, 1)
            .bStats(mapOf(PlatformType.VELOCITY to 3))
            .bStats(10)
            .build().single()

        assertEquals(1, configuration.forPlatform(PlatformType.BUKKIT).projectId)
        assertEquals(3, configuration.forPlatform(PlatformType.VELOCITY).projectId)
        assertEquals(10, configuration.forPlatform(PlatformType.BUNGEECORD).projectId)
    }

    @Test
    fun `a platform without a bStats project is rejected`() {
        val configuration = MetricsBuilder().bStats(PlatformType.BUKKIT, 1).build().single()

        assertThrows(IllegalArgumentException::class.java) { configuration.forPlatform(PlatformType.VELOCITY) }
    }

    @Test
    fun `rejects a repeated bStats project for one platform`() {
        val builder = MetricsBuilder().bStats(PlatformType.BUKKIT, 1)

        assertThrows(IllegalStateException::class.java) { builder.bStats(PlatformType.BUKKIT, 2) }
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
        builder.configure(object : PluginMetrics {
            override val providers = setOf(MetricsProvider.BSTATS)
            override fun simplePie(id: String, value: Supplier<String?>) = this
            override fun advancedPie(id: String, values: Supplier<Map<String, Int>>) = this
            override fun drilldownPie(id: String, values: Supplier<Map<String, Map<String, Int>>>) = this
            override fun singleLineChart(id: String, value: Supplier<Int>) = this
            override fun multiLineChart(id: String, values: Supplier<Map<String, Int>>) = this
            override fun simpleBarChart(id: String, values: Supplier<Map<String, Int>>) = this
            override fun advancedBarChart(id: String, values: Supplier<Map<String, IntArray>>) = this
            override fun close() = Unit
        })
        assertEquals(true, configured)
    }
}
