package org.pnfolder.pnlibrary.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.core.observability.report.SystemReportCollector;

class SystemReportCollectorTest {
    @Test
    void collectExposesOnlyActionableRuntimeSections() {
        Map<String, Object> report = new SystemReportCollector().collect();

        assertTrue(report.get("java") instanceof Map);
        assertTrue(report.get("os") instanceof Map);
        assertTrue(report.get("memory") instanceof Map);
        assertTrue(report.get("threads") instanceof Map);
        assertTrue(report.get("storage") instanceof java.util.List);
        Object health = report.get("health");
        assertTrue(health instanceof Map && ((Map<?, ?>) health).get("signals") instanceof java.util.List);
        assertFalse(report.containsKey("processMemoryMaps"));
        assertFalse(report.containsKey("environmentVariableAnalytics"));
        assertFalse(report.containsKey("collectionHistory"));
    }

    @Test
    void threadDumpIdentifiesLiveThreadsWithoutCollectingItIntoSystemJson() {
        SystemReportCollector collector = new SystemReportCollector();

        assertTrue(collector.threadDump().contains(Thread.currentThread().getName()));
        assertFalse(collector.collect().containsKey("threadDump"));
    }
}
