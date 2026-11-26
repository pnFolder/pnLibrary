package org.pnfolder.pnlibrary.core.diagnostics;

import java.util.Objects;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.diagnostics.report.DiagnosticLevel;
import org.pnfolder.pnlibrary.core.diagnostics.redaction.DiagnosticValueSanitizer;

/** Bounded, deduplicated incident history for one plugin. */
public final class DiagnosticIncidentLog {
    private static final int MAXIMUM_TIMELINE_ENTRIES = 500;

    private final int capacity;
    private final DiagnosticValueSanitizer sanitizer;
    private final ArrayDeque<Map<String, Object>> incidents = new ArrayDeque<>();

    /** Creates a log that keeps at most {@code capacity} distinct incidents. */
    public DiagnosticIncidentLog(int capacity, @NotNull DiagnosticValueSanitizer sanitizer) {
        this.capacity = capacity;
        this.sanitizer = Objects.requireNonNull(sanitizer, "sanitizer");
    }

    /** Adds an occurrence: a repeat of a known incident is counted, a new one pushes out the oldest when full. */
    public void record(@NotNull String plugin, @NotNull DiagnosticLevel level, @NotNull String component,
                       @NotNull String code, @NotNull String message, @Nullable Throwable error,
                       @NotNull Map<String, Object> fields) {
        synchronized (incidents) {
            String now = Instant.now().toString();
            String incidentId = sanitizer.incidentId(plugin, level, component, code, message, error);
            Map<String, Object> existing = find(incidentId);
            if (existing == null) {
                incidents.addLast(newIncident(incidentId, now, level, component, code, message, fields, error));
                while (incidents.size() > capacity) {
                    incidents.removeFirst();
                }
            } else {
                appendOccurrence(existing, now, fields);
            }
        }
    }

    /** Copies every incident and its timeline: the originals keep changing under the lock after this returns. */
    @NotNull
    public List<Map<String, Object>> snapshot() {
        synchronized (incidents) {
            List<Map<String, Object>> copies = new ArrayList<>(incidents.size());
            for (Map<String, Object> incident : incidents) {
                Map<String, Object> copy = new LinkedHashMap<>(incident);
                copy.put("occurrenceTimeline", new ArrayList<>(timelineOf(incident)));
                copies.add(copy);
            }
            return copies;
        }
    }

    /** Counts incidents and occurrences by level and component. */
    @NotNull
    public Map<String, Object> summary() {
        synchronized (incidents) {
            Map<String, Integer> byLevel = new LinkedHashMap<>();
            Map<String, Integer> byComponent = new LinkedHashMap<>();
            long occurrences = 0L;
            long omitted = 0L;
            for (Map<String, Object> incident : incidents) {
                increment(byLevel, text(incident.get("level"), "UNKNOWN"));
                increment(byComponent, text(incident.get("component"), "unknown"));
                occurrences += count(incident, "occurrenceCount", 1L);
                omitted += count(incident, "omittedOccurrences", 0L);
            }
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("incidentCount", incidents.size());
            summary.put("occurrenceCount", occurrences);
            summary.put("omittedOccurrenceCount", omitted);
            summary.put("byLevel", byLevel);
            summary.put("byComponent", byComponent);
            summary.put("oldestUtc", incidents.isEmpty() ? null : incidents.peekFirst().get("firstSeenUtc"));
            summary.put("newestUtc", incidents.isEmpty() ? null : incidents.peekLast().get("lastSeenUtc"));
            return summary;
        }
    }

    private Map<String, Object> find(String incidentId) {
        for (Map<String, Object> incident : incidents) {
            if (incidentId.equals(incident.get("incidentId"))) {
                return incident;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> timelineOf(Map<String, Object> incident) {
        return (List<Object>) incident.get("occurrenceTimeline");
    }

    private static String text(Object value, String fallback) {
        return value == null ? fallback : value.toString();
    }

    private static long count(Map<String, Object> incident, String key, long fallback) {
        Object value = incident.get(key);
        return value instanceof Number ? ((Number) value).longValue() : fallback;
    }

    private static void increment(Map<String, Integer> counts, String key) {
        Integer previous = counts.get(key);
        counts.put(key, previous == null ? 1 : previous + 1);
    }

    private void appendOccurrence(Map<String, Object> incident, String timeUtc, Map<String, Object> fields) {
        incident.put("occurrenceCount", count(incident, "occurrenceCount", 1L) + 1);
        incident.put("lastSeenUtc", timeUtc);
        List<Object> timeline = timelineOf(incident);
        if (timeline.size() < MAXIMUM_TIMELINE_ENTRIES) {
            timeline.add(occurrence(timeUtc, fields));
        } else {
            incident.put("omittedOccurrences", count(incident, "omittedOccurrences", 0L) + 1);
        }
    }

    private Map<String, Object> newIncident(String incidentId, String timeUtc, DiagnosticLevel level,
                                            String component, String code, String message,
                                            Map<String, Object> fields, Throwable error) {
        Map<String, Object> incident = new LinkedHashMap<>();
        incident.put("incidentId", incidentId);
        incident.put("timeUtc", timeUtc);
        incident.put("firstSeenUtc", timeUtc);
        incident.put("lastSeenUtc", timeUtc);
        incident.put("occurrenceCount", 1L);
        incident.put("omittedOccurrences", 0L);
        incident.put("level", level.name());
        incident.put("component", component);
        incident.put("code", code);
        incident.put("message", message);
        incident.put("fields", fields);
        incident.put("origin", error == null ? null : sanitizer.exceptionOrigin(error));
        incident.put("occurrenceTimeline", new ArrayList<>(Collections.singletonList(occurrence(timeUtc, fields))));
        if (error != null) {
            incident.put("exception", sanitizer.exception(error));
        }
        return incident;
    }

    private static Map<String, Object> occurrence(String timeUtc, Map<String, Object> fields) {
        Map<String, Object> occurrence = new LinkedHashMap<>();
        occurrence.put("timeUtc", timeUtc);
        occurrence.put("thread", Thread.currentThread().getName());
        occurrence.put("fields", fields);
        return occurrence;
    }
}
