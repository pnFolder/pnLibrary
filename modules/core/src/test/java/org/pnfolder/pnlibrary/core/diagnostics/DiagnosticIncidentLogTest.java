package org.pnfolder.pnlibrary.core.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.diagnostics.report.DiagnosticLevel;
import org.pnfolder.pnlibrary.core.diagnostics.redaction.DiagnosticValueSanitizer;

class DiagnosticIncidentLogTest {
    private static DiagnosticIncidentLog log(int capacity) {
        return new DiagnosticIncidentLog(capacity, new DiagnosticValueSanitizer());
    }

    private static void add(DiagnosticIncidentLog incidents, String code) {
        incidents.record("plugin", DiagnosticLevel.WARNING, "component", code, "m", null,
            Collections.<String, Object>emptyMap());
    }

    @Test
    void theSameIncidentIsCountedNotRepeated() {
        DiagnosticIncidentLog incidents = log(10);

        for (int index = 0; index < 3; index++) {
            add(incidents, "A");
        }

        List<Map<String, Object>> snapshot = incidents.snapshot();
        assertEquals(1, snapshot.size());
        assertEquals(3L, snapshot.get(0).get("occurrenceCount"));
    }

    @Test
    void theOldestIncidentLeavesWhenTheLogIsFull() {
        DiagnosticIncidentLog incidents = log(2);

        add(incidents, "A");
        add(incidents, "B");
        add(incidents, "C");

        List<Object> codes = new ArrayList<Object>();
        for (Map<String, Object> incident : incidents.snapshot()) {
            codes.add(incident.get("code"));
        }
        assertEquals(Arrays.<Object>asList("B", "C"), codes);
    }

    @Test
    void theTimelineStopsGrowingAndTheRestIsCountedAsOmitted() {
        DiagnosticIncidentLog incidents = log(10);

        for (int index = 0; index < 600; index++) {
            add(incidents, "A");
        }

        Map<String, Object> incident = incidents.snapshot().get(0);
        assertEquals(600L, incident.get("occurrenceCount"));
        assertEquals(500, ((List<?>) incident.get("occurrenceTimeline")).size());
        assertEquals(100L, incident.get("omittedOccurrences"));
    }

    @Test
    void aSnapshotIsACopyThatLaterRecordsDoNotChange() {
        DiagnosticIncidentLog incidents = log(10);
        add(incidents, "A");
        List<Map<String, Object>> before = incidents.snapshot();

        add(incidents, "A");

        assertEquals(1L, before.get(0).get("occurrenceCount"));
        assertEquals(1, ((List<?>) before.get(0).get("occurrenceTimeline")).size());
    }

    @Test
    void theSummaryCountsIncidentsAndOccurrences() {
        DiagnosticIncidentLog incidents = log(10);
        add(incidents, "A");
        add(incidents, "A");
        add(incidents, "B");

        Map<String, Object> summary = incidents.summary();

        assertEquals(2, summary.get("incidentCount"));
        assertEquals(3L, summary.get("occurrenceCount"));
    }
}
