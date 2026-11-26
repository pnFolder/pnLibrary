package org.pnfolder.pnlibrary.core.observability;

import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.diagnostics.report.DiagnosticLevel;
import org.pnfolder.pnlibrary.api.observability.observation.Observation;
import org.pnfolder.pnlibrary.api.observability.observation.ObservationLevel;
import org.pnfolder.pnlibrary.api.observability.observation.ObservationRequest;
import org.pnfolder.pnlibrary.core.diagnostics.DiagnosticsRegistry;
import org.pnfolder.pnlibrary.updates.paths.LibraryPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CompatibilityAdapterTest {
    @Test
    void observationRecordsLandInOneJournalInOrder() throws IOException {
        ObservabilityRuntime runtime = new ObservabilityRuntime(new LibraryPaths(Files.createTempDirectory("compatibility")));
        ObservabilityHub service = new ObservabilityHub(new DiagnosticsRegistry(), runtime);

        service.record(new ObservationRequest(null, null, "new", ObservationLevel.NOTICE));
        service.record(new ObservationRequest(null, null, "second"));

        List<String> messages = new ArrayList<>();
        for (Observation observation : runtime.recent()) {
            messages.add(observation.getMessage());
        }
        assertEquals(Arrays.asList("new", "second"), messages);
    }

    @Test
    void oneDiagnosticCallCreatesOneObservation() throws IOException {
        ObservabilityRuntime runtime = new ObservabilityRuntime(
                new LibraryPaths(Files.createTempDirectory("diagnostic-compatibility")));
        DiagnosticsRegistry registry = new DiagnosticsRegistry();
        ObservabilityHub service = new ObservabilityHub(registry, runtime);
        registry.onActivityEvent((plugin, level, component, code, message, error, fields) -> {
            Map<String, String> data = new LinkedHashMap<>(ObservationConversions.toStringValues(fields));
            data.put("code", code);
            runtime.record(new ObservationRequest(plugin, component, message,
                    ObservationConversions.toObservationLevel(level), data, Collections.<java.nio.file.Path>emptyList(), error));
        });

        service.record("acceptance", DiagnosticLevel.ERROR, "database", "TIMEOUT", "Timed out");

        assertEquals(1, runtime.recent().size());
        assertEquals("TIMEOUT", runtime.recent().get(0).getData().get("code"));
    }

    @Test
    void byteAttachmentKeepsItsDeclaredMetadataWithoutATemporaryFile() throws IOException {
        ObservabilityRuntime runtime = new ObservabilityRuntime(
                new LibraryPaths(Files.createTempDirectory("byte-attachment")));
        byte[] bytes = "server output".getBytes();

        org.pnfolder.pnlibrary.core.observability.store.StoredAttachment attachment =
                runtime.attachBytes("event-1", "latest.log", "text/x-log", bytes);

        assertEquals("latest.log", attachment.getOriginalName());
        assertEquals("text/x-log", attachment.getContentType());
        assertArrayEquals(bytes, runtime.attachmentBytes(runtime.attachments("event-1").get(0)));
    }
}
