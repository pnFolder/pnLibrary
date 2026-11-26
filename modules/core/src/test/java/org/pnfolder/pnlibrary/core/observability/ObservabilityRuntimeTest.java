package org.pnfolder.pnlibrary.core.observability;

import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.observability.observation.Observation;
import org.pnfolder.pnlibrary.api.observability.observation.ObservationLevel;
import org.pnfolder.pnlibrary.api.observability.observation.ObservationQuery;
import org.pnfolder.pnlibrary.api.observability.report.ComponentStatus;
import org.pnfolder.pnlibrary.core.observability.report.ObservabilityReportSnapshot;
import org.pnfolder.pnlibrary.updates.paths.LibraryPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObservabilityRuntimeTest {
    @Test
    void captureStoresOneNormalizedObservationAndItsFiles() throws IOException {
        Path root = Files.createTempDirectory("observability-runtime");
        Path log = root.resolve("latest.log");
        Files.write(log, "failure details".getBytes());
        ObservabilityRuntime runtime = new ObservabilityRuntime(new LibraryPaths(root), () -> 123L);

        Observation captured = runtime.capture(scope -> scope.plugin("acceptance")
                .source("startup")
                .message("Plugin started")
                .data("attempt", 2)
                .files(log));

        assertEquals(123L, captured.getTimestamp());
        assertEquals(Collections.singletonList(captured), runtime.recent());
        assertEquals(1, runtime.attachments(captured.getId()).size());
    }

    @Test
    void failureIsRecordedOnceWithErrorDetails() throws IOException {
        ObservabilityRuntime runtime = new ObservabilityRuntime(
                new LibraryPaths(Files.createTempDirectory("observability-failure")));

        runtime.failure(new IllegalStateException("database unavailable"),
                scope -> scope.plugin("acceptance").source("database"));

        List<Observation> observations = runtime.recent();
        assertEquals(1, observations.size());
        assertEquals(ObservationLevel.ERROR, observations.get(0).getLevel());
        assertEquals("java.lang.IllegalStateException", observations.get(0).getErrorType());
        assertEquals("database unavailable", observations.get(0).getMessage());
    }

    @Test
    void statusReplacesThePreviousValueForTheSameComponent() throws IOException {
        ObservabilityRuntime runtime = new ObservabilityRuntime(
                new LibraryPaths(Files.createTempDirectory("observability-status")));

        runtime.status(new ComponentStatus("acceptance", "database", "starting"));
        ComponentStatus ready = runtime.status(new ComponentStatus("acceptance", "database", "ready"));

        assertEquals(Collections.singletonList(ready), runtime.statuses());
    }

    @Test
    void concurrentCapturesAreNotLostOrDuplicated() throws Exception {
        ObservabilityRuntime runtime = new ObservabilityRuntime(
                new LibraryPaths(Files.createTempDirectory("observability-concurrent")));
        ExecutorService executor = Executors.newFixedThreadPool(8);

        for (int index = 0; index < 100; index++) {
            String message = "event-" + index;
            executor.submit(() -> runtime.capture(scope -> scope.message(message)));
        }
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

        List<Observation> observations = runtime.recent(new ObservationQuery(200));
        assertEquals(100, observations.size());
        Set<String> ids = new HashSet<>();
        for (Observation observation : observations) {
            ids.add(observation.getId());
        }
        assertEquals(100, ids.size());
    }

    @Test
    void analyticsExposesSeverityTimelineAndStatusHealth() throws IOException {
        ObservabilityRuntime runtime = new ObservabilityRuntime(
                new LibraryPaths(Files.createTempDirectory("observability-analytics")));

        runtime.capture(scope -> scope.plugin("alpha").source("startup").message("ready"));
        runtime.failure(new IllegalStateException("broken"), scope -> scope.plugin("alpha").source("database"));
        runtime.status(new ComponentStatus("alpha", "database", "degraded"));

        Map<String, Object> analytics = runtime.analytics();
        assertEquals(2, analytics.get("eventCount"));
        assertEquals(1, analytics.get("errorCount"));
        assertEquals(1, analytics.get("statusCount"));
        assertTrue(!((List<?>) analytics.get("recent")).isEmpty());
        assertEquals("attention", analytics.get("status"));
    }

    @Test
    void supportSnapshotCarriesAnalyticsAlongsideJournal() throws IOException {
        ObservabilityRuntime runtime = new ObservabilityRuntime(
                new LibraryPaths(Files.createTempDirectory("observability-snapshot")));
        runtime.capture(scope -> scope.message("startup"));

        ObservabilityReportSnapshot snapshot = ObservabilityReportSnapshot.of(runtime);

        assertTrue(snapshot.getAnalytics().containsKey("eventCount"));
        assertTrue(snapshot.getJournal().length > 0);
    }
}
