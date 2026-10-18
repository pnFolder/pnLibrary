package ru.privatenull.pnlibrary.core.metrics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.metrics.MetricsProvider
import ru.privatenull.pnlibrary.api.metrics.MetricsProviderConfiguration
import ru.privatenull.pnlibrary.spi.metrics.NoopMetricsFactory

class NativePluginMetricsFactoryTest {
    private class NativePlugin

    private val calls = mutableListOf<String>()
    private val factory = NativePluginMetricsFactory(
        ownerType = NativePlugin::class.java,
        platformName = "Test",
        bStats = { _, projectId -> calls += "bstats:$projectId"; session() },
        fastStats = { _, token -> calls += "faststats:$token"; session() },
    )
    private val providers = listOf(
        MetricsProviderConfiguration(MetricsProvider.BSTATS, projectId = 7),
        MetricsProviderConfiguration(MetricsProvider.FASTSTATS, token = "token"),
    )

    @Test
    fun `native plugin owners open every enabled provider`() {
        factory.open(NativePlugin(), providers)
        assertEquals(listOf("bstats:7", "faststats:token"), calls)
    }

    @Test
    fun `other owners are rejected before a provider is opened`() {
        val error = assertThrows(IllegalArgumentException::class.java) { factory.open("owner", providers) }
        assertEquals("Test metrics owner must be a Test plugin; received java.lang.String", error.message)
        assertEquals(emptyList<String>(), calls)
    }

    private fun session() = NoopMetricsFactory.open(Any(), providers)
}
