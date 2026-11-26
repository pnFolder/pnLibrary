package org.pnfolder.pnlibrary.core.runtime.details;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.tasks.info.TaskSnapshot;

/** Renders task snapshots as maps for the diagnostics report. */
public final class RuntimeTaskDetails {
    private RuntimeTaskDetails() {
    }

    public static @NotNull List<Map<String, Object>> entries(@NotNull List<TaskSnapshot> snapshots) {
        List<Map<String, Object>> entries = new ArrayList<Map<String, Object>>();
        for (TaskSnapshot task : snapshots) {
            Map<String, Object> entry = new LinkedHashMap<String, Object>();
            entry.put("id", task.getId().getValue());
            entry.put("name", task.getName());
            entry.put("owner", task.getOwnerName());
            entry.put("execution", task.getExecutionKind().name());
            entry.put("status", task.getStatus().name());
            entry.put("runCount", task.getRunCount());
            entry.put("skippedCount", task.getSkippedCount());
            entry.put("lastFailure", task.getLastFailure());
            entries.add(entry);
        }
        return entries;
    }

    public static @NotNull Map<String, Object> summary(@NotNull List<TaskSnapshot> snapshots) {
        Map<String, Integer> byStatus = new LinkedHashMap<String, Integer>();
        Map<String, Integer> byExecution = new LinkedHashMap<String, Integer>();
        Map<String, Integer> failuresByOwner = new LinkedHashMap<String, Integer>();
        Map<String, Integer> failureMessages = new LinkedHashMap<String, Integer>();
        long runCount = 0, skippedCount = 0;
        int failed = 0;
        for (TaskSnapshot task : snapshots) {
            count(byStatus, task.getStatus().name());
            count(byExecution, task.getExecutionKind().name());
            runCount += task.getRunCount();
            skippedCount += task.getSkippedCount();
            if (task.getLastFailure() != null) {
                failed++;
                count(failuresByOwner, task.getOwnerName());
                String message = task.getLastFailure();
                count(failureMessages, message.length() > 256 ? message.substring(0, 256) : message);
            }
        }
        Map<String, Object> summary = new LinkedHashMap<String, Object>();
        summary.put("count", snapshots.size());
        summary.put("byStatus", byStatus);
        summary.put("byExecution", byExecution);
        summary.put("runCount", runCount);
        summary.put("skippedCount", skippedCount);
        summary.put("failedCount", failed);
        summary.put("failuresByOwner", failuresByOwner);
        summary.put("failureMessages", failureMessages);
        return summary;
    }

    private static void count(Map<String, Integer> counts, String key) {
        Integer current = counts.get(key);
        counts.put(key, current == null ? 1 : current + 1);
    }
}
