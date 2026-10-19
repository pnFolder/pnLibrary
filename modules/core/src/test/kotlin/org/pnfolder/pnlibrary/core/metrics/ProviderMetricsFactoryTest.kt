package org.pnfolder.pnlibrary.core.metrics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.metrics.MetricsProvider
import org.pnfolder.pnlibrary.api.metrics.MetricsProviderConfiguration
import org.pnfolder.pnlibrary.api.metrics.PluginMetrics
import org.pnfolder.pnlibrary.spi.metrics.NoopMetricsFactory

class ProviderMetricsFactoryTest {
    private val bStats = MetricsProviderConfiguration(MetricsProvider.BSTATS, projectId = 7)
    private val fastStats = MetricsProviderConfiguration(MetricsProvider.FASTSTATS, token = "token")

    @Test
    fun `single enabled provider is returned without a composite wrapper`() {
        val factory = RecordingFactory()
        val session = factory.open(Any(), listOf(bStats, fastStats.copy(enabled = false)))
        assertSame(factory.sessions.single(), session)
        assertEquals(listOf("bstats:7"), factory.calls)
    }

    @Test
    fun `several enabled providers are composed`() {
        val factory = RecordingFactory()
        val session = factory.open(Any(), listOf(bStats, fastStats))
        assertTrue(session is CompositePluginMetrics)
        assertEquals(listOf("bstats:7", "faststats:token"), factory.calls)
    }

    @Test
    fun `already opened providers are closed when a later provider fails`() {
        val factory = RecordingFactory(failFastStats = true)
        assertThrows(IllegalStateException::class.java) { factory.open(Any(), listOf(bStats, fastStats)) }
        assertEquals(listOf("close"), factory.closed)
    }

    @Test
    fun `missing credentials and empty selections are rejected`() {
        val factory = RecordingFactory()
        assertThrows(IllegalArgumentException::class.java) {
            factory.open(Any(), listOf(MetricsProviderConfiguration(MetricsProvider.BSTATS)))
        }
        assertThrows(IllegalArgumentException::class.java) { factory.open(Any(), listOf(bStats.copy(enabled = false))) }
    }

    private class RecordingFactory(private val failFastStats: Boolean = false) : ProviderMetricsFactory() {
        val calls = mutableListOf<String>()
        val closed = mutableListOf<String>()
        val sessions = mutableListOf<PluginMetrics>()

        override fun openBStats(owner: Any, projectId: Int): PluginMetrics {
            calls += "bstats:$projectId"
            return session()
        }

        override fun openFastStats(owner: Any, token: String): PluginMetrics {
            calls += "faststats:$token"
            check(!failFastStats) { "FastStats unavailable" }
            return session()
        }

        private fun session(): PluginMetrics {
            val delegate = NoopMetricsFactory.open(Any(), listOf(MetricsProviderConfiguration(MetricsProvider.BSTATS, projectId = 1)))
            val session = object : PluginMetrics by delegate {
                override fun close() { closed += "close" }
            }
            sessions += session
            return session
        }
    }
}
