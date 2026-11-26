package org.pnfolder.pnlibrary.core.logging;

import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.logging.LogLevel;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;

/** One incident of the buffer; its fields are touched only under the buffer's lock. */
final class LoggedIncident {
    final String id;
    final LogLevel level;
    final String plugin;
    final String message;
    final String exceptionType;
    final Map<String, Object> origin;
    final String fullError;
    final String consoleBlock;
    final String firstSeenUtc;
    String lastSeenUtc;
    long lastSummaryAtMs;
    long count = 1;
    long omittedOccurrences;
    final ArrayDeque<IncidentOccurrence> occurrences = new ArrayDeque<IncidentOccurrence>();

    LoggedIncident(String id, LogLevel level, String plugin, String message, @Nullable String exceptionType,
                   @Nullable Map<String, Object> origin, @Nullable String fullError, String consoleBlock,
                   String firstSeenUtc, long lastSummaryAtMs) {
        this.id = id;
        this.level = level;
        this.plugin = plugin;
        this.message = message;
        this.exceptionType = exceptionType;
        this.origin = origin;
        this.fullError = fullError;
        this.consoleBlock = consoleBlock;
        this.firstSeenUtc = firstSeenUtc;
        this.lastSeenUtc = firstSeenUtc;
        this.lastSummaryAtMs = lastSummaryAtMs;
    }

    Map<String, Object> toMap() {
        Map<String, Object> timeline = new LinkedHashMap<String, Object>();
        timeline.put("count", count);
        timeline.put("firstSeenUtc", firstSeenUtc);
        timeline.put("lastSeenUtc", lastSeenUtc);
        java.util.List<Object> times = new java.util.ArrayList<Object>();
        for (IncidentOccurrence occurrence : occurrences) {
            Map<String, Object> entry = new LinkedHashMap<String, Object>();
            entry.put("timeUtc", occurrence.timeUtc);
            entry.put("thread", occurrence.thread);
            times.add(entry);
        }
        timeline.put("timeline", times);
        timeline.put("omitted", omittedOccurrences);
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("incidentId", id);
        map.put("level", level.name());
        map.put("plugin", plugin);
        map.put("message", message);
        map.put("exceptionType", exceptionType);
        map.put("origin", origin);
        map.put("occurrences", timeline);
        map.put("fullError", fullError);
        map.put("consoleBlock", consoleBlock);
        return map;
    }
}
