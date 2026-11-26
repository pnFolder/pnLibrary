package org.pnfolder.pnlibrary.core.runtime.details;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/** Builds the analytics section of the diagnostics report. */
public final class RuntimeAnalytics {
    private RuntimeAnalytics() {
    }

    public static @NotNull Map<String, Object> of(int taskCount, int updateCount, int taskFailures, int failedUpdates,
                                                  @NotNull Map<String, Object> diagnosticSummary, @NotNull Map<String, Object> history,
                                                  @NotNull Map<String, Object> nativeLogs, @NotNull Map<String, Object> reports,
                                                  @NotNull Map<String, Object> observability) {
        Object occurrences = history.get("occurrenceCount");
        int incidentCount = occurrences instanceof Number ? ((Number) occurrences).intValue() : 0;
        Map<String, Object> analytics = new LinkedHashMap<>();
        analytics.put("status", status(taskFailures, failedUpdates, incidentCount));
        analytics.put("signals", signals(taskFailures, failedUpdates, incidentCount, occurrences));
        analytics.put("resources", resources(taskCount, updateCount, diagnosticSummary, nativeLogs, reports));
        analytics.put("counts", counts(taskFailures, failedUpdates, history, observability, reports));
        return analytics;
    }

    private static String status(int taskFailures, int failedUpdates, int incidentCount) {
        if (taskFailures > 0 || failedUpdates > 0) {
            return "critical";
        }
        return incidentCount > 0 ? "attention" : "healthy";
    }

    private static List<Object> signals(int taskFailures, int failedUpdates, int incidentCount, Object occurrences) {
        List<Object> signals = new ArrayList<>();
        if (taskFailures > 0) {
            signals.add(signal("taskFailures", "critical", taskFailures));
        }
        if (failedUpdates > 0) {
            signals.add(signal("updateFailures", "critical", failedUpdates));
        }
        if (incidentCount > 0) {
            signals.add(signal("diagnosticIncidents", "attention", occurrences));
        }
        return signals;
    }

    private static Map<String, Object> signal(String code, String severity, Object count) {
        Map<String, Object> signal = new LinkedHashMap<>();
        signal.put("code", code);
        signal.put("severity", severity);
        signal.put("count", count);
        return signal;
    }

    private static Map<String, Object> resources(int taskCount, int updateCount, Map<String, Object> diagnosticSummary,
                                                 Map<String, Object> nativeLogs, Map<String, Object> reports) {
        Map<String, Object> resources = new LinkedHashMap<>();
        resources.put("registeredTasks", taskCount);
        resources.put("registeredUpdates", updateCount);
        resources.put("diagnosticEntries",
                diagnosticSummary.get("count") != null ? diagnosticSummary.get("count") : diagnosticSummary.get("total"));
        resources.put("nativeLogRecords", nativeLogs.get("count"));
        resources.put("reportsCreated", reports.get("created"));
        resources.put("reportHistoryFiles", nested(reports.get("history"), "fileCount"));
        return resources;
    }

    private static Map<String, Object> counts(int taskFailures, int failedUpdates, Map<String, Object> history,
                                              Map<String, Object> observability, Map<String, Object> reports) {
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("taskFailures", taskFailures);
        counts.put("updateFailures", failedUpdates);
        counts.put("diagnosticIncidents", history.get("incidentCount"));
        counts.put("observabilityErrors", observability.get("errorCount"));
        counts.put("reportsFailed", reports.get("failed"));
        counts.put("reportSuccessRate", reports.get("successRate"));
        counts.put("uploads", nested(reports.get("uploadLedger"), "uploaded"));
        return counts;
    }

    private static Object nested(Object map, String key) {
        return map instanceof Map ? ((Map<?, ?>) map).get(key) : null;
    }
}
