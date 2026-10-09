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
    }
}
