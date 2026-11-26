package org.pnfolder.pnlibrary.api.observability;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.observability.observation.Observation;
import org.pnfolder.pnlibrary.api.observability.observation.ObservationLevel;
import org.pnfolder.pnlibrary.api.observability.observation.ObservationQuery;
import org.pnfolder.pnlibrary.api.observability.observation.ObservationRequest;
import org.pnfolder.pnlibrary.api.observability.report.ComponentStatus;
import org.pnfolder.pnlibrary.api.observability.report.ObservabilityReport;
import org.pnfolder.pnlibrary.api.observability.report.ObservabilityReportRequest;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ObservabilityDslTest {
    @Test
    void captureDescribesContextAndFilesWithoutTechnicalIdentifiers() {
        Path log = Paths.get("logs/latest.log");
        RecordingObservabilityService service = new RecordingObservabilityService();

        service.capture(scope -> scope
                .plugin("acceptance")
                .source("Updater")
                .message("Проверка обновления завершилась ошибкой")
                .data("repository", "pnFolder/pnLibrary")
                .files(log));

        ObservationRequest request = service.lastRequest;
        assertEquals("acceptance", request.getPlugin());
        assertEquals("Updater", request.getSource());
        assertEquals("Проверка обновления завершилась ошибкой", request.getMessage());
        assertEquals(Collections.singletonMap("repository", "pnFolder/pnLibrary"), request.getData());
        assertEquals(Collections.singletonList(log), request.getFiles());
    }

    @Test
    void failureCarriesThrowableWithoutDuplicatingItInMetadata() {
        IllegalStateException error = new IllegalStateException("GitHub timeout");
        RecordingObservabilityService service = new RecordingObservabilityService();

        service.failure(error, scope -> scope.plugin("acceptance").source("Updater"));

        assertSame(error, service.lastRequest.getError());
        assertEquals(ObservationLevel.ERROR, service.lastRequest.getLevel());
        assertEquals("GitHub timeout", service.lastRequest.getMessage());
    }

    @Test
    void storedObservationBoundsFreeFormDiagnosticData() {
        Map<String, String> data = new LinkedHashMap<>();
        for (int i = 1; i <= 40; i++) {
            data.put("key-" + i, repeat('v', 3_000));
        }
        ObservationRequest request = new ObservationRequest(repeat('p', 200), repeat('s', 200), repeat('m', 2_000),
                ObservationLevel.INFO, data, Collections.emptyList(), new IllegalStateException(repeat('e', 5_000)));

        Observation observation = Observation.from(request);

        assertEquals(128, observation.getPlugin().length());
        assertEquals(128, observation.getSource().length());
        assertEquals(1_024, observation.getMessage().length());
        assertEquals(32, observation.getData().size());
        assertEquals(2_048, observation.getData().values().iterator().next().length());
        assertEquals(4_096, observation.getErrorMessage().length());
    }

    private static String repeat(char character, int count) {
        char[] characters = new char[count];
        java.util.Arrays.fill(characters, character);
        return new String(characters);
    }

    private static final class RecordingObservabilityService implements ObservabilityService {
        private ObservationRequest lastRequest;

        @NotNull
        @Override
        public Observation record(@NotNull ObservationRequest request) {
            lastRequest = request;
            return Observation.from(request);
        }

        @NotNull
        @Override
        public List<Observation> recent(@NotNull ObservationQuery query) {
            return Collections.emptyList();
        }

        @NotNull
        @Override
        public ComponentStatus status(@NotNull ComponentStatus status) {
            return status;
        }

        @NotNull
        @Override
        public ObservabilityReport createReport(@NotNull ObservabilityReportRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() {
        }
    }
}
