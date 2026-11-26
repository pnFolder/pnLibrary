package org.pnfolder.pnlibrary.core.stats;

import java.util.Objects;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.pnfolder.pnlibrary.common.lang.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.core.journal.JournalRecord;

/**
 * Aggregated numbers about one moment of the server.
 *
 * <p>Privacy: the snapshot holds counts and plugin ids only. It never holds player names or UUIDs, task names, message
 * texts, addresses or stack traces, so it is safe to keep in the journal and to put in a support archive.
 */
public final class StatsSnapshot {
    /** Journal record type of a stored snapshot. */
    public static final String RECORD_TYPE = JournalRecord.STATS_TYPE;

    private final long timeMillis;
    private final int tasksActive;
    private final int tasksFailed;
    private final long taskRuns;
    private final long taskSkipped;
    private final long eventsCalled;
    private final long eventsDenied;
    private final int warnings24h;
    private final int errors24h;
    private final List<Pair<String, Integer>> errorSources;

    /**
     * @param timeMillis when the numbers were taken, in epoch milliseconds
     * @param tasksActive tasks that are waiting or running now
     * @param tasksFailed tasks that ended with an error, among those still remembered
     * @param taskRuns how many times tasks ran
     * @param taskSkipped how many runs were skipped because a task was still busy or its condition was false
     * @param eventsCalled how many times events were called since the start
     * @param eventsDenied how many of them were cancelled or denied
     * @param warnings24h warnings in the journal over the last 24 hours
     * @param errors24h errors in the journal over the last 24 hours
     * @param errorSources plugins with the most errors over the last 24 hours, worst first
     */
    public StatsSnapshot(long timeMillis, int tasksActive, int tasksFailed, long taskRuns, long taskSkipped,
                         long eventsCalled, long eventsDenied, int warnings24h, int errors24h,
                         @NotNull List<Pair<String, Integer>> errorSources) {
        this.timeMillis = timeMillis;
        this.tasksActive = tasksActive;
        this.tasksFailed = tasksFailed;
        this.taskRuns = taskRuns;
        this.taskSkipped = taskSkipped;
        this.eventsCalled = eventsCalled;
        this.eventsDenied = eventsDenied;
        this.warnings24h = warnings24h;
        this.errors24h = errors24h;
        this.errorSources = Objects.requireNonNull(errorSources, "errorSources");
    }

    public long getTimeMillis() {
        return timeMillis;
    }

    public int getTasksActive() {
        return tasksActive;
    }

    public int getTasksFailed() {
        return tasksFailed;
    }

    public long getTaskRuns() {
        return taskRuns;
    }

    public long getTaskSkipped() {
        return taskSkipped;
    }

    public long getEventsCalled() {
        return eventsCalled;
    }

    public long getEventsDenied() {
        return eventsDenied;
    }

    public int getWarnings24h() {
        return warnings24h;
    }

    public int getErrors24h() {
        return errors24h;
    }

    public @NotNull List<Pair<String, Integer>> getErrorSources() {
        return errorSources;
    }

    /** The snapshot as journal details; {@link #fromDetails} reads it back. */
    public @NotNull Map<String, String> toDetails() {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("tasksActive", String.valueOf(tasksActive));
        details.put("tasksFailed", String.valueOf(tasksFailed));
        details.put("taskRuns", String.valueOf(taskRuns));
        details.put("taskSkipped", String.valueOf(taskSkipped));
        details.put("eventsCalled", String.valueOf(eventsCalled));
        details.put("eventsDenied", String.valueOf(eventsDenied));
        details.put("warnings24h", String.valueOf(warnings24h));
        details.put("errors24h", String.valueOf(errors24h));
        StringBuilder sources = new StringBuilder();
        for (Pair<String, Integer> source : errorSources) {
            if (sources.length() > 0) {
                sources.append(',');
            }
            sources.append(source.getFirst()).append(':').append(source.getSecond());
        }
        details.put("errorSources", sources.toString());
        return details;
    }

    /** Reads a snapshot written by {@link #toDetails}, or returns null when a number is missing or broken. */
    public static @Nullable StatsSnapshot fromDetails(long timeMillis, @NotNull Map<String, String> details) {
        Integer tasksActive = integer(details.get("tasksActive"));
        Integer tasksFailed = integer(details.get("tasksFailed"));
        Long taskRuns = number(details.get("taskRuns"));
        Long taskSkipped = number(details.get("taskSkipped"));
        Long eventsCalled = number(details.get("eventsCalled"));
        Long eventsDenied = number(details.get("eventsDenied"));
        Integer warnings = integer(details.get("warnings24h"));
        Integer errors = integer(details.get("errors24h"));
        if (tasksActive == null || tasksFailed == null || taskRuns == null || taskSkipped == null
            || eventsCalled == null || eventsDenied == null || warnings == null || errors == null) {
            return null;
        }
        return new StatsSnapshot(timeMillis, tasksActive, tasksFailed, taskRuns, taskSkipped, eventsCalled,
            eventsDenied, warnings, errors, parseSources(details.get("errorSources")));
    }

    private static List<Pair<String, Integer>> parseSources(String text) {
        List<Pair<String, Integer>> sources = new ArrayList<>();
        if (text == null) {
            return sources;
        }
        for (String part : text.split(",", -1)) {
            if (part.isEmpty()) {
                continue;
            }
            int colon = part.lastIndexOf(':');
            Integer count = integer(colon < 0 ? part : part.substring(colon + 1));
            if (count != null) {
                sources.add(new Pair<>(colon < 0 ? part : part.substring(0, colon), count));
            }
        }
        return sources;
    }

    private static Integer integer(String value) {
        try {
            return value == null ? null : Integer.valueOf(value);
        } catch (NumberFormatException error) {
            return null;
        }
    }

    private static Long number(String value) {
        try {
            return value == null ? null : Long.valueOf(value);
        } catch (NumberFormatException error) {
            return null;
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof StatsSnapshot)) {
            return false;
        }
        StatsSnapshot s = (StatsSnapshot) other;
        return timeMillis == s.timeMillis && tasksActive == s.tasksActive && tasksFailed == s.tasksFailed
            && taskRuns == s.taskRuns && taskSkipped == s.taskSkipped && eventsCalled == s.eventsCalled
            && eventsDenied == s.eventsDenied && warnings24h == s.warnings24h && errors24h == s.errors24h
            && errorSources.equals(s.errorSources);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(timeMillis, tasksActive, tasksFailed, taskRuns, taskSkipped, eventsCalled,
            eventsDenied, warnings24h, errors24h, errorSources);
    }

    @Override
    public String toString() {
        return "StatsSnapshot(timeMillis=" + timeMillis + ", tasksActive=" + tasksActive + ", tasksFailed=" + tasksFailed
            + ", taskRuns=" + taskRuns + ", taskSkipped=" + taskSkipped + ", eventsCalled=" + eventsCalled
            + ", eventsDenied=" + eventsDenied + ", warnings24h=" + warnings24h + ", errors24h=" + errors24h
            + ", errorSources=" + errorSources + ")";
    }
}
