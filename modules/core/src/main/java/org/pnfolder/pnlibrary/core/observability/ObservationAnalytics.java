package org.pnfolder.pnlibrary.core.observability;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.observability.observation.Observation;
import org.pnfolder.pnlibrary.api.observability.observation.ObservationLevel;
import org.pnfolder.pnlibrary.api.observability.report.ComponentStatus;
import org.pnfolder.pnlibrary.core.observability.store.StoredAttachment;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Counts, rates and health computed from the stored observations, attachments and component statuses. */
public final class ObservationAnalytics {
    private static final int MAX_ANALYTICS_EVENTS = 32;
    private static final int MAX_ANALYTICS_MESSAGES = 32;
    private static final long HOUR_MS = 60 * 60_000L;
    private static final long DAY_MS = 24 * HOUR_MS;
    private static final long ANALYTICS_WINDOW_MS = 5 * 60_000L;
    private static final String UNKNOWN = "[unknown]";

    private ObservationAnalytics() {
    }

    /** Builds the analytics map in the order the support report shows it. */
    @NotNull
    public static Map<String, Object> compute(@NotNull List<Observation> events,
                                              @NotNull List<StoredAttachment> attachments,
                                              @NotNull List<ComponentStatus> statuses) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", health(events, statuses));
        result.put("eventCount", events.size());
        result.put("byLevel", countBy(events, Field.LEVEL));
        result.put("bySource", countBy(events, Field.SOURCE));
        result.put("byMessage", topMessages(events));
        result.put("byPlugin", countBy(events, Field.PLUGIN));
        result.put("errorCount", errorCount(events));
        result.put("errorTypes", errorTypes(events));
        result.put("statusCount", statuses.size());
        result.put("statusStates", countStates(statuses));
        result.put("recent", recentEvents(events));
        result.put("oldestUtc", boundary(events, true));
        result.put("newestUtc", boundary(events, false));
        result.put("activity", activity(events));
        result.putAll(attachmentFigures(attachments));
        return result;
    }

    private enum Field { LEVEL, SOURCE, PLUGIN }

    private static String health(List<Observation> events, List<ComponentStatus> statuses) {
        for (Observation event : events) {
            if (event.getLevel() == ObservationLevel.CRITICAL) {
                return "critical";
            }
        }
        for (Observation event : events) {
            if (event.getLevel() == ObservationLevel.ERROR) {
                return "attention";
            }
        }
        for (ComponentStatus status : statuses) {
            if (status.getState().equalsIgnoreCase("failed") || status.getState().equalsIgnoreCase("degraded")) {
                return "attention";
            }
        }
        return "healthy";
    }

    private static Map<String, Integer> countBy(List<Observation> events, Field field) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Observation event : events) {
            String key;
            switch (field) {
                case LEVEL:
                    key = event.getLevel().name();
                    break;
                case SOURCE:
                    key = event.getSource() == null ? UNKNOWN : event.getSource();
                    break;
                default:
                    key = event.getPlugin() == null ? UNKNOWN : event.getPlugin();
                    break;
            }
            increment(counts, key);
        }
        return counts;
    }

    private static void increment(Map<String, Integer> counts, String key) {
        Integer count = counts.get(key);
        counts.put(key, count == null ? 1 : count + 1);
    }

    private static Map<String, Integer> topMessages(List<Observation> events) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Observation event : events) {
            increment(counts, event.getMessage());
        }
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(counts.entrySet());
        Collections.sort(sorted, Collections.reverseOrder(Map.Entry.<String, Integer>comparingByValue()));
        Map<String, Integer> top = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : sorted.subList(0, Math.min(sorted.size(), MAX_ANALYTICS_MESSAGES))) {
            top.put(entry.getKey(), entry.getValue());
        }
        return top;
    }

    private static int errorCount(List<Observation> events) {
        int count = 0;
        for (Observation event : events) {
            count += event.getErrorType() != null ? 1 : 0;
        }
        return count;
    }

    private static Map<String, Integer> errorTypes(List<Observation> events) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Observation event : events) {
            if (event.getErrorType() != null) {
                increment(counts, event.getErrorType());
            }
        }
        return counts;
    }

    private static Map<String, Integer> countStates(List<ComponentStatus> statuses) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ComponentStatus status : statuses) {
            increment(counts, status.getState());
        }
        return counts;
    }

    private static List<Map<String, Object>> recentEvents(List<Observation> events) {
        List<Map<String, Object>> recent = new ArrayList<>();
        List<Observation> last = events.subList(Math.max(0, events.size() - MAX_ANALYTICS_EVENTS), events.size());
        for (Observation event : last) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", event.getId());
            entry.put("timestampUtc", Instant.ofEpochMilli(event.getTimestamp()).toString());
            entry.put("plugin", event.getPlugin() == null ? UNKNOWN : event.getPlugin());
            entry.put("source", event.getSource() == null ? UNKNOWN : event.getSource());
            entry.put("level", event.getLevel().name());
            entry.put("message", event.getMessage());
            entry.put("errorType", event.getErrorType());
            entry.put("dataKeys", new ArrayList<>(new TreeSet<>(event.getData().keySet())));
            recent.add(entry);
        }
        return recent;
    }

    private static String boundary(List<Observation> events, boolean oldest) {
        if (events.isEmpty()) {
            return null;
        }
        long chosen = events.get(0).getTimestamp();
        for (Observation event : events) {
            chosen = oldest ? Math.min(chosen, event.getTimestamp()) : Math.max(chosen, event.getTimestamp());
        }
        return Instant.ofEpochMilli(chosen).toString();
    }

    private static Map<String, Object> attachmentFigures(List<StoredAttachment> attachments) {
        long total = 0L;
        Map<String, Integer> byType = new LinkedHashMap<>();
        Map<String, Integer> byObservation = new LinkedHashMap<>();
        for (StoredAttachment attachment : attachments) {
            total += attachment.getSize();
            increment(byType, attachment.getContentType());
            increment(byObservation, attachment.getObservationId());
        }
        Map<String, Object> figures = new LinkedHashMap<>();
        figures.put("attachmentCount", attachments.size());
        figures.put("attachmentBytes", total);
        figures.put("attachmentsByContentType", byType);
        figures.put("attachmentsByObservation", byObservation);
        figures.put("attachmentSize", attachmentSize(attachments, total));
        return figures;
    }

    private static Map<String, Object> attachmentSize(List<StoredAttachment> attachments, long total) {
        Map<String, Object> size = new LinkedHashMap<>();
        if (attachments.isEmpty()) {
            size.put("minBytes", null);
            size.put("maxBytes", null);
            size.put("averageBytes", null);
            return size;
        }
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        for (StoredAttachment attachment : attachments) {
            min = Math.min(min, attachment.getSize());
            max = Math.max(max, attachment.getSize());
        }
        size.put("minBytes", min);
        size.put("maxBytes", max);
        size.put("averageBytes", (double) total / attachments.size());
        return size;
    }

    private static Map<String, Object> activity(List<Observation> events) {
        long now = System.currentTimeMillis();
        List<Observation> window = within(events, now, ANALYTICS_WINDOW_MS);
        List<Observation> lastHour = within(events, now, HOUR_MS);
        List<Observation> lastDay = within(events, now, DAY_MS);
        int windowErrors = 0;
        for (Observation event : window) {
            boolean severe = event.getLevel() == ObservationLevel.ERROR || event.getLevel() == ObservationLevel.CRITICAL;
            windowErrors += event.getErrorType() != null || severe ? 1 : 0;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("windowMinutes", ANALYTICS_WINDOW_MS / 60_000);
        result.put("eventsLastWindow", window.size());
        result.put("errorsLastWindow", windowErrors);
        result.put("errorRateLastWindow", windowErrors / (double) Math.max(window.size(), 1));
        result.put("activeSourcesLastWindow", distinctSources(window, true));
        result.put("activePluginsLastWindow", distinctSources(window, false));
        result.put("eventsLastHour", lastHour.size());
        result.put("errorsLastHour", errorCount(lastHour));
        result.put("eventsLastDay", lastDay.size());
        result.put("errorsLastDay", errorCount(lastDay));
        result.put("eventRatePerMinuteLastHour", lastHour.size() / 60.0);
        result.put("errorRatePerMinuteLastDay", errorCount(lastDay) / (24 * 60.0));
        return result;
    }

    private static List<Observation> within(List<Observation> events, long now, long periodMs) {
        List<Observation> found = new ArrayList<>();
        for (Observation event : events) {
            if (now - event.getTimestamp() <= periodMs) {
                found.add(event);
            }
        }
        return found;
    }

    private static int distinctSources(List<Observation> events, boolean sources) {
        Set<String> names = new LinkedHashSet<>();
        for (Observation event : events) {
            String name = sources ? event.getSource() : event.getPlugin();
            if (name != null) {
                names.add(name);
            }
        }
        return names.size();
    }
}
