package org.pnfolder.pnlibrary.core.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticRegistration;
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticsContributor;
import org.pnfolder.pnlibrary.api.diagnostics.report.DiagnosticLevel;

class DiagnosticsRegistrationLifecycleTest {
    private static DiagnosticsContributor okContributor() {
        return () -> Collections.singletonMap("ok", true);
    }

    @Test
    void closeIsObservableIdempotentAndRemovesContributor() {
        DiagnosticsRegistry registry = new DiagnosticsRegistry();
        DiagnosticRegistration registration = registry.register("demo", okContributor());

        assertFalse(registration.isClosed());
        assertTrue(registry.snapshot("demo").toString().contains("ok"));

        registration.close();
        registration.close();

        assertTrue(registration.isClosed());
        assertFalse(registry.snapshot("demo").toString().contains("ok"));
    }

    @Test
    void closingAnOldRegistrationKeepsTheSameContributorRegisteredAgain() {
        DiagnosticsRegistry registry = new DiagnosticsRegistry();
        DiagnosticsContributor contributor = okContributor();
        DiagnosticRegistration old = registry.register("demo", contributor);
        registry.register("demo", contributor);

        old.close();

        assertTrue(registry.snapshot("demo").toString().contains("ok"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void eventSnapshotDoesNotChangeWhenTheIncidentRepeatsLater() {
        DiagnosticsRegistry registry = new DiagnosticsRegistry();
        registry.record("demo", DiagnosticLevel.WARNING, "db", "slow", "Slow query", null,
            Collections.emptyMap());
        List<Map<String, Object>> events = (List<Map<String, Object>>) registry.eventSnapshot().get("demo");
        Map<String, Object> before = events.get(0);

        registry.record("demo", DiagnosticLevel.WARNING, "db", "slow", "Slow query", null,
            Collections.emptyMap());

        assertEquals(1L, before.get("occurrenceCount"));
        assertEquals(1, ((List<?>) before.get("occurrenceTimeline")).size());
    }

    @Test
    void aFailingListenerIsReportedAndDoesNotRejectTheEvent() {
        DiagnosticsRegistry registry = new DiagnosticsRegistry();
        List<String> failures = new ArrayList<>();
        registry.onInternalFailure((what, error) -> failures.add(what));
        registry.onEventsChanged(() -> {
            throw new IllegalStateException("disk full");
        });
        registry.onActivityEvent((plugin, level, component, code, message, error, fields) -> {
            throw new IllegalStateException("bridge broke");
        });

        registry.record("demo", DiagnosticLevel.WARNING, "db", "slow", "Slow query", null,
            Collections.emptyMap());

        assertEquals(1, ((List<?>) registry.eventSnapshot().get("demo")).size());
        assertEquals(
            new HashSet<>(Arrays.asList(
                "The diagnostic activity listener failed", "The diagnostic history callback failed")),
            new HashSet<>(failures));
    }
}
