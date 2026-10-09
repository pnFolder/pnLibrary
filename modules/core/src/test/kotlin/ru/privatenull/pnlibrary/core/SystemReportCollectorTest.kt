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
        assertTrue(limits.containsKey("cpuThrottledMicros"))
        assertTrue(limits.containsKey("cpuThrottleEvents"))
        val collectionHistory = analytics["collectionHistory"] as? Map<*, *>
            ?: error("collectionHistory section is missing")
        assertTrue(collectionHistory.containsKey("samplesWithSignals"))
        assertTrue((collectionHistory["trend"] as? Map<*, *>)?.containsKey("heapUsedRatioDelta") == true)
        assertTrue((collectionHistory["trend"] as? Map<*, *>)?.containsKey("systemCpuLoadDelta") == true)
        val network = report["networkAnalytics"] as? Map<*, *>
            ?: error("networkAnalytics section is missing")
        assertTrue(network.containsKey("ipv4AddressCount"))
        assertTrue(network.containsKey("ipv6AddressCount"))
        assertTrue(report["processIo"] is Map<*, *>)
        assertTrue(report["loadAverage"] is Map<*, *>)
        val threads = report["threads"] as? Map<*, *>
            ?: error("threads section is missing")
        assertTrue(threads["deadlockedThreads"] is List<*>)
        (threads["topCpuThreads"] as? List<*>)?.firstOrNull()?.let { top ->
            assertTrue((top as? Map<*, *>)?.containsKey("cpuShare") == true)
        }
        val fileSystems = report["fileSystems"] as? List<*>
            ?: error("fileSystems section is missing")
        assertTrue(fileSystems.firstOrNull() is Map<*, *>)
        assertTrue((fileSystems.firstOrNull() as? Map<*, *>)?.containsKey("writable") == true)
    }
}
