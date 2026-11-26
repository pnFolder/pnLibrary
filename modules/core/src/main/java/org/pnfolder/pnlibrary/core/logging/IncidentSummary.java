package org.pnfolder.pnlibrary.core.logging;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** The totals of a set of incidents, as the diagnostics report shows them. */
final class IncidentSummary {
    private IncidentSummary() {
    }

    static Map<String, Object> of(Collection<LoggedIncident> incidents) {
        long occurrences = 0;
        long omitted = 0;
        Map<String, Integer> byLevel = new LinkedHashMap<>();
        Map<String, Integer> byPlugin = new LinkedHashMap<>();
        Map<String, Integer> exceptionTypes = new LinkedHashMap<>();
        LoggedIncident newest = null;
        for (LoggedIncident incident : incidents) {
            occurrences += incident.count;
            omitted += incident.omittedOccurrences;
            byLevel.merge(incident.level.name(), 1, Integer::sum);
            byPlugin.merge(incident.plugin, 1, Integer::sum);
            if (incident.exceptionType != null) {
                exceptionTypes.merge(incident.exceptionType, 1, Integer::sum);
            }
            if (newest == null || incident.lastSeenUtc.compareTo(newest.lastSeenUtc) > 0) {
                newest = incident;
            }
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("incidentCount", incidents.size());
        summary.put("occurrenceCount", occurrences);
        summary.put("omittedOccurrenceCount", omitted);
        summary.put("byLevel", byLevel);
        summary.put("byPlugin", byPlugin);
        summary.put("exceptionTypes", exceptionTypes);
        summary.put("oldestUtc", incidents.isEmpty() ? null : incidents.iterator().next().firstSeenUtc);
        summary.put("newestUtc", newest == null ? null : newest.lastSeenUtc);
        return summary;
    }
}
