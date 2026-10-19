package org.pnfolder.pnlibrary.core.runtime

/** Builds the derived health view of one runtime diagnostic snapshot. */
internal object RuntimeAnalytics {
    fun of(
        taskCount: Int,
        updateCount: Int,
        taskFailures: Int,
        failedUpdates: Int,
        diagnosticSummary: Map<String, Any?>,
        history: Map<String, Any?>,
        nativeLogs: Map<String, Any?>,
        reports: Map<String, Any?>,
        observability: Map<String, Any?>,
    ): Map<String, Any?> {
        val incidentCount = (history["occurrenceCount"] as? Number)?.toInt() ?: 0
        return linkedMapOf(
            "status" to when {
                taskFailures > 0 || failedUpdates > 0 -> "critical"
                incidentCount > 0 -> "attention"
                else -> "healthy"
            },
            "signals" to buildList {
                if (taskFailures > 0) {
                    add(linkedMapOf("code" to "taskFailures", "severity" to "critical", "count" to taskFailures))
                }
                if (failedUpdates > 0) {
                    add(linkedMapOf("code" to "updateFailures", "severity" to "critical", "count" to failedUpdates))
                }
                if (incidentCount > 0) {
                    add(linkedMapOf("code" to "diagnosticIncidents", "severity" to "attention", "count" to history["occurrenceCount"]))
                }
            },
            "resources" to linkedMapOf(
                "registeredTasks" to taskCount,
                "registeredUpdates" to updateCount,
                "diagnosticEntries" to (diagnosticSummary["count"] ?: diagnosticSummary["total"]),
                "nativeLogRecords" to nativeLogs["count"],
                "reportsCreated" to reports["created"],
                "reportHistoryFiles" to (reports["history"] as? Map<*, *>)?.get("fileCount"),
            ),
            "counts" to linkedMapOf(
                "taskFailures" to taskFailures,
                "updateFailures" to failedUpdates,
                "diagnosticIncidents" to history["incidentCount"],
                "observabilityErrors" to observability["errorCount"],
                "reportsFailed" to reports["failed"],
                "reportSuccessRate" to reports["successRate"],
                "uploads" to (reports["uploadLedger"] as? Map<*, *>)?.get("uploaded"),
            ),
        )
    }
}
