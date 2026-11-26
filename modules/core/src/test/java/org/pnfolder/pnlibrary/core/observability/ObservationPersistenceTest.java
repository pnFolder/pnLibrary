package org.pnfolder.pnlibrary.core.observability;

import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.observability.observation.Observation;
import org.pnfolder.pnlibrary.api.observability.observation.ObservationLevel;
import org.pnfolder.pnlibrary.api.observability.observation.ObservationRequest;
import org.pnfolder.pnlibrary.core.observability.report.ObservabilityReportSnapshot;
import org.pnfolder.pnlibrary.core.observability.store.AttachmentStore;
import org.pnfolder.pnlibrary.core.observability.store.ObservationJournal;
import org.pnfolder.pnlibrary.core.observability.store.ObservationRetention;
import org.pnfolder.pnlibrary.core.observability.store.StoredAttachment;
import org.pnfolder.pnlibrary.updates.paths.LibraryPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObservationPersistenceTest {
    private static final long DAY = 24L * 60 * 60 * 1000;

    private static List<String> messages(List<Observation> observations) {
        List<String> messages = new ArrayList<>();
        for (Observation observation : observations) {
            messages.add(observation.getMessage());
        }
        return messages;
    }

    @Test
    void journalReloadsValidObservationsAndSkipsMalformedLines() throws IOException {
        Path root = Files.createTempDirectory("observations");
        Observation observation = Observation.from(new ObservationRequest(null, null, "started"), 100);
        try (ObservationJournal journal = new ObservationJournal(root.resolve("observations.jsonl"), () -> 100)) {
            journal.append(observation);
        }
        Files.write(root.resolve("observations.jsonl"), "not-json\n".getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.APPEND);

        try (ObservationJournal journal = new ObservationJournal(root.resolve("observations.jsonl"), () -> 100)) {
            assertEquals(Collections.singletonList(observation), journal.recent());
        }
    }

    @Test
    void journalDoesNotPersistLocalAttachmentPaths() throws IOException {
        Path root = Files.createTempDirectory("observation-paths");
        Path source = root.resolve("server.log");
        Files.write(source, "secret".getBytes(StandardCharsets.UTF_8));
        Observation observation = Observation.from(new ObservationRequest(null, null, "failed", ObservationLevel.INFO,
                Collections.<String, String>emptyMap(), Collections.singletonList(source)));

        try (ObservationJournal journal = new ObservationJournal(root.resolve("observations.jsonl"))) {
            journal.append(observation);
        }

        try (ObservationJournal journal = new ObservationJournal(root.resolve("observations.jsonl"))) {
            assertEquals("failed", journal.recent().get(0).getMessage());
        }
    }

    @Test
    void attachmentStoreInfersContentTypeAndRemovesOrphan() throws IOException {
        Path root = Files.createTempDirectory("observation-attachments");
        Path source = root.resolve("latest.log");
        Files.write(source, "hello".getBytes(StandardCharsets.UTF_8));
        AttachmentStore store = new AttachmentStore(root);

        StoredAttachment attachment = store.save("event-1", source);
        assertEquals("text/plain", attachment.getContentType());
        assertTrue(Files.exists(attachment.getStoredPath()));

        store.removeOrphans(Collections.<String>emptySet());
        assertFalse(Files.exists(attachment.getStoredPath()));
    }

    @Test
    void attachmentMetadataSurvivesStoreRestart() throws IOException {
        Path root = Files.createTempDirectory("attachment-reload");
        Path source = root.resolve("configuration.json");
        Files.write(source, "{}".getBytes(StandardCharsets.UTF_8));
        StoredAttachment saved = new AttachmentStore(root).save("event-1", source);

        StoredAttachment reloaded = new AttachmentStore(root).forObservation("event-1").get(0);

        assertEquals(saved.withStoredPath(reloaded.getStoredPath()), reloaded);
        assertTrue(Files.exists(reloaded.getStoredPath()));
    }

    @Test
    void attachmentStoreRejectsExecutableFiles() throws IOException {
        Path root = Files.createTempDirectory("attachment-executable");
        Path source = root.resolve("unsafe.jar");
        Files.write(source, "not really a jar".getBytes(StandardCharsets.UTF_8));

        assertThrows(IllegalArgumentException.class, () -> new AttachmentStore(root).save("event-1", source));
    }

    @Test
    void retentionKeepsCriticalObservationsAndExpiresOldInformation() {
        long now = 10L * DAY;
        Observation oldInfo = Observation.from(new ObservationRequest(null, null, "old"), 1);
        Observation oldCritical = Observation.from(
                new ObservationRequest(null, null, "critical", ObservationLevel.CRITICAL), 1);

        List<Observation> retained = ObservationRetention.retained(Arrays.asList(oldInfo, oldCritical), now);

        assertEquals(Collections.singletonList(oldCritical), retained);
    }

    @Test
    void runtimeRemovesExpiredObservationsDuringStartup() throws IOException {
        Path root = Files.createTempDirectory("observation-retention");
        Observation old = Observation.from(new ObservationRequest(null, null, "expired"), 1);
        try (ObservationJournal journal = new ObservationJournal(root.resolve("observations.jsonl"), () -> 1)) {
            journal.append(old);
        }

        ObservabilityRuntime runtime = new ObservabilityRuntime(new LibraryPaths(root), () -> 30L * DAY);

        assertTrue(runtime.recent().isEmpty());
    }

    @Test
    void journalAppliesRetentionWhileNewObservationsAreRecorded() throws IOException {
        Path root = Files.createTempDirectory("observation-live-retention");
        Observation old = Observation.from(new ObservationRequest(null, null, "expired"), 1);
        Observation current = Observation.from(new ObservationRequest(null, null, "current"), 30L * DAY);
        try (ObservationJournal journal = new ObservationJournal(root.resolve("observations.jsonl"), current::getTimestamp)) {
            journal.append(old);
            journal.append(current);

            assertEquals(Collections.singletonList(current), journal.recent());
        }
    }

    @Test
    void authorRetentionOverridesLevelInBothDirections() throws IOException {
        AtomicLong now = new AtomicLong();
        ObservabilityRuntime runtime = new ObservabilityRuntime(
                new LibraryPaths(Files.createTempDirectory("observation-keep")), now::get);
        runtime.capture(scope -> scope.message("short critical").level(ObservationLevel.CRITICAL)
                .keepFor(Duration.ofDays(2)));
        runtime.capture(scope -> scope.message("long info").level(ObservationLevel.INFO).keepFor(Duration.ofDays(365)));
        runtime.capture(scope -> scope.message("plain info"));

        now.set(10 * DAY);
        runtime.maintain();

        assertEquals(Collections.singletonList("long info"), messages(runtime.recent()));
        assertEquals(365 * DAY, Long.parseLong(runtime.recent().get(0).getData().get("pn.keepUntilMs")));
        assertFalse(runtime.recent().get(0).getData().containsKey("pn.keepForMs"));
    }

    @Test
    void reportJournalHasReadableDatesAndExpiry() throws IOException {
        Path root = Files.createTempDirectory("observation-report");
        ObservabilityRuntime runtime = new ObservabilityRuntime(new LibraryPaths(root), () -> 0);
        runtime.capture(scope -> scope.plugin("pnMarket").source("auction").message("lot lost")
                .keepFor(Duration.ofDays(1)));

        String line = new String(ObservabilityReportSnapshot.of(runtime).getJournal(), StandardCharsets.UTF_8);

        assertTrue(line.contains("\"timeUtc\":\"1970-01-01T00:00:00Z\""), line);
        assertTrue(line.contains("\"expiresUtc\":\"1970-01-02T00:00:00Z\""), line);
        assertTrue(line.contains("\"source\":\"auction\""), line);
    }
}
