package org.pnfolder.pnlibrary.core.observability.history;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncidentHistoryStoreTest {
    @TempDir
    Path temporaryDirectory;

    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void disabledEncryptionNeverPersistsPlaintextHistory() throws IOException {
        IncidentHistoryStore history = new IncidentHistoryStore(temporaryDirectory, null, 30, 1_024, clock);

        history.save(Collections.singletonList(Collections.<String, Object>singletonMap("message", "secret")),
                Collections.<String, Object>emptyMap());

        try (Stream<Path> files = Files.list(temporaryDirectory)) {
            assertFalse(files.findAny().isPresent());
        }
    }

    @Test
    void pruneRemovesHistoryOlderThanTheRetentionPeriodWithoutSaving() throws IOException {
        Path old = temporaryDirectory.resolve("old.pndlog");
        Path fresh = temporaryDirectory.resolve("fresh.pndlog");
        Files.write(old, new byte[2]);
        Files.write(fresh, new byte[2]);
        Files.setLastModifiedTime(old, FileTime.from(clock.instant().minusSeconds(40L * 86_400)));
        Files.setLastModifiedTime(fresh, FileTime.from(clock.instant().minusSeconds(86_400)));
        IncidentHistoryStore history = new IncidentHistoryStore(temporaryDirectory, null, 30, 1_024, clock);

        history.prune();

        assertFalse(Files.exists(old));
        assertTrue(Files.exists(fresh));
    }

    @Test
    void fileListingRespectsAggregateEncryptedByteBudget() throws IOException {
        Files.write(temporaryDirectory.resolve("a.pndlog"), new byte[]{1, 1, 1});
        Files.write(temporaryDirectory.resolve("b.pndlog"), new byte[]{2, 2, 2});
        Files.write(temporaryDirectory.resolve("ignored.txt"), new byte[1]);
        IncidentHistoryStore history = new IncidentHistoryStore(temporaryDirectory, null, 30, 4, clock);

        List<Map.Entry<String, byte[]>> files = history.files();

        assertEquals(1, files.size());
        assertEquals("a.pndlog", files.get(0).getKey());
        assertEquals(3, files.get(0).getValue().length);
    }

    @Test
    void rejectsInvalidRetentionLimits() {
        assertThrows(IllegalArgumentException.class,
                () -> new IncidentHistoryStore(temporaryDirectory, null, 0, 1, clock));
        assertThrows(IllegalArgumentException.class,
                () -> new IncidentHistoryStore(temporaryDirectory, null, 1, 0, clock));
    }
}
