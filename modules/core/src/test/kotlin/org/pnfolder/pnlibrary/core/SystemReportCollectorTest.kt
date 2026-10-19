package org.pnfolder.pnlibrary.core

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.core.observability.report.SystemReportCollector

class SystemReportCollectorTest {
    @Test
    fun `collect exposes only actionable runtime sections`() {
        val report = SystemReportCollector().collect()

        assertTrue(report["java"] is Map<*, *>)
        assertTrue(report["os"] is Map<*, *>)
        assertTrue(report["memory"] is Map<*, *>)
        assertTrue(report["threads"] is Map<*, *>)
        assertTrue(report["storage"] is List<*>)
        assertTrue((report["health"] as? Map<*, *>)?.get("signals") is List<*>)
        assertFalse(report.containsKey("processMemoryMaps"))
        assertFalse(report.containsKey("environmentVariableAnalytics"))
        assertFalse(report.containsKey("collectionHistory"))
    }

    @Test
    fun `thread dump identifies live threads without collecting it into system json`() {
        val collector = SystemReportCollector()

        assertTrue(collector.threadDump().contains(Thread.currentThread().name))
        assertFalse(collector.collect().containsKey("threadDump"))
    }
}
