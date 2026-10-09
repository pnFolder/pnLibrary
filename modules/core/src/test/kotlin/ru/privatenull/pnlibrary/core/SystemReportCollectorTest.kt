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
        assertTrue((analytics["resources"] as? Map<*, *>)?.containsKey("processTcpEstablished") == true)
        assertTrue((analytics["resources"] as? Map<*, *>)?.containsKey("blockedThreads") == true)
        assertTrue((analytics["resources"] as? Map<*, *>)?.containsKey("loadAverage") == true)
        assertTrue((analytics["resources"] as? Map<*, *>)?.containsKey("processRss") == true)
        assertTrue((analytics["resources"] as? Map<*, *>)?.containsKey("voluntaryContextSwitches") == true)
        assertTrue((analytics["resources"] as? Map<*, *>)?.containsKey("blockedTimeMs") == true)
        assertTrue((analytics["resources"] as? Map<*, *>)?.containsKey("freeSpaceBytes") == true)
        assertTrue((analytics["resources"] as? Map<*, *>)?.containsKey("processVirtualMemory") == true)
        assertTrue((analytics["resources"] as? Map<*, *>)?.containsKey("processNativeThreadCount") == true)
        assertTrue((analytics["resources"] as? Map<*, *>)?.containsKey("processPriority") == true)
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
        assertTrue(limits.containsKey("memoryUsedRatio"))
        assertTrue(limits.containsKey("cpuQuotaCores"))
        val collectionHistory = analytics["collectionHistory"] as? Map<*, *>
            ?: error("collectionHistory section is missing")
        assertTrue(collectionHistory.containsKey("samplesWithSignals"))
        assertTrue(collectionHistory.containsKey("signalCodeFrequency"))
        assertTrue((collectionHistory["trend"] as? Map<*, *>)?.containsKey("heapUsedRatioDelta") == true)
        assertTrue((collectionHistory["trend"] as? Map<*, *>)?.containsKey("systemCpuLoadDelta") == true)
        assertTrue((collectionHistory["trend"] as? Map<*, *>)?.containsKey("readBytesDelta") == true)
        assertTrue((collectionHistory["trend"] as? Map<*, *>)?.containsKey("tcpEstablishedDelta") == true)
        assertTrue((collectionHistory["trend"] as? Map<*, *>)?.containsKey("containerCpuThrottledDelta") == true)
        assertTrue((collectionHistory["trend"] as? Map<*, *>)?.containsKey("gcCollectionTimeMsDelta") == true)
        assertTrue((collectionHistory["trend"] as? Map<*, *>)?.containsKey("threadCountDelta") == true)
        assertTrue((collectionHistory["trend"] as? Map<*, *>)?.containsKey("heapUsedBytesDelta") == true)
        assertTrue((collectionHistory["trend"] as? Map<*, *>)?.containsKey("freePhysicalMemoryBytesDelta") == true)
        assertTrue((collectionHistory["trend"] as? Map<*, *>)?.containsKey("processRssDelta") == true)
        assertTrue((collectionHistory["trend"] as? Map<*, *>)?.containsKey("blockedThreadsDelta") == true)
        assertTrue((collectionHistory["trend"] as? Map<*, *>)?.containsKey("highestMemoryPoolRatioDelta") == true)
        assertTrue((collectionHistory["trend"] as? Map<*, *>)?.containsKey("unloadedClassCountDelta") == true)
        val network = report["networkAnalytics"] as? Map<*, *>
            ?: error("networkAnalytics section is missing")
        assertTrue(network.containsKey("ipv4AddressCount"))
        assertTrue(network.containsKey("ipv6AddressCount"))
        assertTrue(report["processIo"] is Map<*, *>)
        assertTrue(report["loadAverage"] is Map<*, *>)
        assertTrue(report["processNetwork"] is Map<*, *>)
        assertTrue(report["environmentVariableAnalytics"] is Map<*, *>)
        assertTrue(report["processStatus"] is Map<*, *>)
        assertTrue(report["processLimits"] is Map<*, *>)
        assertTrue(report["processScheduling"] is Map<*, *>)
        assertTrue(report["capabilities"] is Map<*, *>)
        assertTrue((analytics["capabilities"] as? Map<*, *>)?.containsKey("threadCpuTimeSupported") == true)
        assertTrue((analytics["resources"] as? Map<*, *>)?.containsKey("maxOpenFiles") == true)
        val threads = report["threads"] as? Map<*, *>
            ?: error("threads section is missing")
        val os = report["os"] as? Map<*, *>
            ?: error("os section is missing")
        assertTrue(os["cpuTopology"] is Map<*, *>)
        val java = report["java"] as? Map<*, *>
            ?: error("java section is missing")
        assertTrue((java["classpathAnalytics"] as? Map<*, *>)?.containsKey("duplicateNameCount") == true)
        assertTrue((java["classpathAnalytics"] as? Map<*, *>)?.containsKey("duplicateNames") == true)
        assertTrue((java["modulePathAnalytics"] as? Map<*, *>)?.containsKey("entryCount") == true)
        assertTrue((java["argumentAnalytics"] as? Map<*, *>)?.containsKey("garbageCollectorOptions") == true)
        assertTrue(threads["deadlockedThreads"] is List<*>)
        assertTrue(threads["contention"] is Map<*, *>)
        (threads["topCpuThreads"] as? List<*>)?.firstOrNull()?.let { top ->
            assertTrue((top as? Map<*, *>)?.containsKey("cpuShare") == true)
        }
        val fileSystems = report["fileSystems"] as? List<*>
            ?: error("fileSystems section is missing")
        assertTrue(fileSystems.firstOrNull() is Map<*, *>)
        assertTrue((fileSystems.firstOrNull() as? Map<*, *>)?.containsKey("writable") == true)
    }
}
