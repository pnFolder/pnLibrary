package org.pnfolder.pnlibrary.core.diagnostics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/** Bounded analytics over the incident history of every plugin, built without invoking contributors. */
final class DiagnosticHistoryAnalytics {
    static final int DEFAULT_LIMIT = 64;

    private DiagnosticHistoryAnalytics() {
    }

    /**
     * Summarizes {@code incidentsByPlugin} (plugin key in report order, its incidents oldest first).
     *
     * @param limit how many of the most recent incidents to list; kept within {@code 1..64}
     */
    @NotNull
    static Map<String, Object> analyze(@NotNull Map<String, List<Map<String, Object>>> incidentsByPlugin, int limit) {
        final List<String> owners = new ArrayList<String>();
        final List<Map<String, Object>> incidents = new ArrayList<Map<String, Object>>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : incidentsByPlugin.entrySet()) {
            for (Map<String, Object> incident : entry.getValue()) {
                owners.add(entry.getKey());
                incidents.add(incident);
            }
        }
        long occurrences = 0L;
        long omitted = 0L;
        Map<String, Integer> byPlugin = new LinkedHashMap<String, Integer>();
        Map<String, Integer> byLevel = new LinkedHashMap<String, Integer>();
        Map<String, Integer> byComponent = new LinkedHashMap<String, Integer>();
        for (int index = 0; index < incidents.size(); index++) {
            Map<String, Object> incident = incidents.get(index);
            occurrences += number(incident.get("occurrenceCount"), 1L);
            omitted += number(incident.get("omittedOccurrences"), 0L);
            increment(byPlugin, owners.get(index));
            increment(byLevel, text(incident.get("level"), "UNKNOWN"));
            increment(byComponent, text(incident.get("component"), "unknown"));
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("incidentCount", incidents.size());
        result.put("occurrenceCount", occurrences);
        result.put("omittedOccurrenceCount", omitted);
        result.put("byPlugin", byPlugin);
        result.put("byLevel", byLevel);
        result.put("byComponent", byComponent);
        result.put("recent", recent(owners, incidents, Math.max(1, Math.min(limit, DEFAULT_LIMIT))));
        return result;
    }

    private static List<Map<String, Object>> recent(List<String> owners, List<Map<String, Object>> incidents,
                                                    int limit) {
        List<Integer> order = new ArrayList<Integer>();
        for (int index = 0; index < incidents.size(); index++) {
            order.add(index);
        }
        final List<Map<String, Object>> source = incidents;
        Collections.sort(order, (left, right) -> text(source.get(left).get("lastSeenUtc"), "").compareTo(text(source.get(right).get("lastSeenUtc"), "")));
        List<Map<String, Object>> recent = new ArrayList<Map<String, Object>>();
        for (int index = Math.max(0, order.size() - limit); index < order.size(); index++) {
            int position = order.get(index);
            recent.add(summary(owners.get(position), incidents.get(position)));
        }
        return recent;
    }

    private static Map<String, Object> summary(String plugin, Map<String, Object> incident) {
        Map<String, Object> summary = new LinkedHashMap<String, Object>();
        summary.put("plugin", plugin);
        for (String key : new String[] {"incidentId", "level", "component", "code", "message", "firstSeenUtc",
            "lastSeenUtc", "occurrenceCount", "omittedOccurrences"}) {
            summary.put(key, incident.get(key));
        }
        return summary;
    }

    private static long number(Object value, long fallback) {
        return value instanceof Number ? ((Number) value).longValue() : fallback;
    }

    private static String text(Object value, String fallback) {
        return value == null ? fallback : value.toString();
    }

    private static void increment(Map<String, Integer> counts, String key) {
        Integer previous = counts.get(key);
        counts.put(key, previous == null ? 1 : previous + 1);
    }
}
