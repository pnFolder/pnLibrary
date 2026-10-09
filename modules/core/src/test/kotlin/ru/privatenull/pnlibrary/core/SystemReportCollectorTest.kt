package ru.privatenull.pnlibrary.core

import ru.privatenull.pnlibrary.core.observability.report.SystemReportCollector
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue

class SystemReportCollectorTest {

    @Test
    fun `collect exposes derived runtime analytics`() {
        val report = SystemReportCollector().collect()

        val analytics = report["analytics"] as? Map<*, *>
            ?: error("analytics section is missing")
        assertTrue(analytics["resources"] is Map<*, *>)
        assertTrue(analytics["signals"] is List<*>)
        assertTrue(analytics["counts"] is Map<*, *>)
        assertTrue(analytics.containsKey("generatedUtc"))

        val runtimeDistribution = analytics["runtimeDistribution"] as? Map<*, *>
            ?: error("runtimeDistribution section is missing")
        assertTrue(runtimeDistribution.containsKey("uptimeSeconds"))
        assertTrue(runtimeDistribution.containsKey("classPathEntryCount"))
        assertTrue(runtimeDistribution.containsKey("systemPropertyCount"))
        assertTrue((analytics["signalSummary"] as? Map<*, *>)?.containsKey("critical") == true)
        assertTrue((analytics["coverage"] as? Map<*, *>)?.containsKey("javaRuntime") == true)
        val host = analytics["hostDistribution"] as? Map<*, *>
            ?: error("hostDistribution section is missing")
        assertTrue(host.containsKey("availableProcessors"))
        assertTrue(host.containsKey("totalPhysicalMemoryBytes"))
        val limits = analytics["containerLimits"] as? Map<*, *>
            ?: error("containerLimits section is missing")
        assertTrue(limits.containsKey("memoryLimitBytes"))
        assertTrue(limits.containsKey("cpuQuotaMicros"))
        val collectionHistory = analytics["collectionHistory"] as? Map<*, *>
            ?: error("collectionHistory section is missing")
        assertTrue(collectionHistory.containsKey("samplesWithSignals"))
        assertTrue((collectionHistory["trend"] as? Map<*, *>)?.containsKey("heapUsedRatioDelta") == true)
        val network = report["networkAnalytics"] as? Map<*, *>
            ?: error("networkAnalytics section is missing")
        assertTrue(network.containsKey("ipv4AddressCount"))
        assertTrue(network.containsKey("ipv6AddressCount"))
    }
}
