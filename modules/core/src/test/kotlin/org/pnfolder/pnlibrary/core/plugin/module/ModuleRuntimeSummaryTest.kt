package org.pnfolder.pnlibrary.core.plugin.module

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.metrics.MetricsProvider
import org.pnfolder.pnlibrary.api.metrics.MetricsProviderConfiguration
import org.pnfolder.pnlibrary.api.platform.PlatformType
import org.pnfolder.pnlibrary.api.plugin.ModuleId
import org.pnfolder.pnlibrary.api.plugin.PluginMetadata

class ModuleRuntimeSummaryTest {
    private val metadata = PluginMetadata(
        ModuleId.of("demo"), "Demo", "1.0.0", "pnFolder",
        PlatformType.BUKKIT, "Paper", "21", 21,
    )

    @Test
    fun `describes the concrete platform without losing its family`() {
        assertEquals("Bukkit · Paper", ModuleRuntimeSummary.platform(metadata))
        assertEquals(
            "Velocity",
            ModuleRuntimeSummary.platform(metadata.copyFor(PlatformType.VELOCITY, "Velocity")),
        )
    }

    @Test
    fun `omits services that were not configured`() {
        assertNull(ModuleRuntimeSummary.metrics(emptyList(), false))
        val bStats = MetricsProviderConfiguration(MetricsProvider.BSTATS, projectId = 123)
        val fastStats = MetricsProviderConfiguration(MetricsProvider.FASTSTATS, token = "token")
        assertEquals("disabled · bStats #123", ModuleRuntimeSummary.metrics(listOf(bStats), false))
        assertEquals("enabled · bStats #123, FastStats", ModuleRuntimeSummary.metrics(listOf(bStats, fastStats), true))
        assertEquals("disabled", ModuleRuntimeSummary.placeholderApi(false, "ready"))
        assertEquals("unavailable", ModuleRuntimeSummary.placeholderApi(true, null))
    }

    private fun PluginMetadata.copyFor(platform: PlatformType, implementation: String) = PluginMetadata(
        id, name, version, authors, platform, implementation, javaVersion, javaFeature,
    )
}
