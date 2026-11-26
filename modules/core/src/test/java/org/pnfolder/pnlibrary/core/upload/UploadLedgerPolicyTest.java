package org.pnfolder.pnlibrary.core.upload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UploadLedgerPolicyTest {
    @TempDir
    Path temporaryDirectory;

    private final Clock clock = Clock.fixed(Instant.ofEpochSecond(1_000), ZoneOffset.UTC);

    private static String read(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    @Test
    void usesCurrentTimeWhenReceiptHasNoCreationTimestamp() throws Exception {
        Path file = temporaryDirectory.resolve("ledger.json");
        new UploadLedger(file, clock).record(receipt("report", 0), 1);

        assertTrue(read(file).contains("\"deleteAt\":87400"));
    }

    @Test
    void recordReplacesAnExistingBackendReceiptInsteadOfDuplicatingIt() throws Exception {
        Path file = temporaryDirectory.resolve("ledger.json");
        UploadLedger ledger = new UploadLedger(file, clock);

        ledger.record(receipt("report", 100), 1);
        ledger.record(receipt("report", 200), 2);

        List<UploadLedgerEntry> entries = new UploadLedgerPersistence(file, clock).read();
        assertEquals(1, entries.size());
        assertEquals(173_000, entries.get(0).getDeleteAt());
    }

    @Test
    void cleanupDeletesOnlyEntriesWhoseScheduleIsDue() throws Exception {
        Path file = temporaryDirectory.resolve("ledger.json");
        UploadLedger ledger = new UploadLedger(file, clock);
        ledger.record(receipt("due", 100), 0);
        UploadLedgerPersistence persistence = new UploadLedgerPersistence(file, clock);
        persistence.write(Arrays.asList(entry("due", 999), entry("future", 1_001)));
        final List<String> deletedIds = new ArrayList<>();
        UploadProvider provider = new UploadProvider() {
            @Override
            public @NotNull String getBackendId() {
                return "test";
            }

            @Override
            public @NotNull UploadReceipt upload(@NotNull String payload) {
                return receipt("report", 1_000);
            }

            @Override
            public boolean delete(@NotNull UploadReceipt receipt) {
                deletedIds.add(receipt.getId());
                return true;
            }
        };

        assertEquals(1, ledger.cleanup(provider));
        assertEquals(Collections.singletonList("due"), deletedIds);
        List<String> left = new ArrayList<>();
        for (UploadLedgerEntry remaining : persistence.read()) {
            left.add(remaining.getId());
        }
        assertEquals(Collections.singletonList("future"), left);
    }

    @Test
    void corruptLedgerIsQuarantinedBeforeRecovery() throws Exception {
        Path file = temporaryDirectory.resolve("ledger.json");
        Files.write(file, "not-json".getBytes(StandardCharsets.UTF_8));

        new UploadLedger(file, clock).record(receipt("report", 1_000), 1);

        Path quarantined = temporaryDirectory.resolve("ledger.json.corrupt-1000");
        assertEquals("not-json", read(quarantined));
        assertTrue(read(file).contains("report"));
    }

    private static UploadReceipt receipt(String id, long createdAt) {
        return new UploadReceipt(URI.create("https://example.com/" + id), id, "delete-token", createdAt, 0, "test");
    }

    private static UploadLedgerEntry entry(String id, long deleteAt) {
        return new UploadLedgerEntry("test", "https://example.com/" + id, id, "delete-token", deleteAt);
    }
}
