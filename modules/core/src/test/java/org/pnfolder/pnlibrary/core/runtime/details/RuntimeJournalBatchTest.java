package org.pnfolder.pnlibrary.core.runtime.details;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.logging.LogLevel;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryConfig;
import org.pnfolder.pnlibrary.core.journal.JournalRecord;
import org.pnfolder.pnlibrary.spi.platform.plugin.PlatformOwnership;

class RuntimeJournalBatchTest {
    private final PlatformOwnership ownership = new PlatformOwnership() {
        @Override
        public @NotNull Map<String, String> ownerDetails(@NotNull Object owner) {
            return Collections.singletonMap("id", "plugin");
        }
    };

    @Test
    void aBurstIsWrittenInFewerBlocksThanRecordsAndNothingDisappearsWithoutACount() throws IOException {
        Path folder = Files.createTempDirectory("pnlibrary-journal-batch");
        RuntimeJournal journal = new RuntimeJournal(folder.resolve("journal"), new PnLibraryConfig(), ownership, 0L, "/pn events");
        int total = 20_000;

        for (int i = 0; i < total; i++) {
            journal.record(this, i % 50 == 0 ? LogLevel.ERROR : LogLevel.INFO, "event " + i, null);
        }
        journal.close();

        long failed = (Long) journal.summary().get("failedWrites");
        int stored = journal.getStore().read(100_000).size();
        assertEquals((long) total, stored + failed);
        assertTrue(journal.getStore().getBlocksWritten().get() < total, "blocks: " + journal.getStore().getBlocksWritten().get());
    }

    @Test
    void recordsRecordedOneAfterAnotherAreAllStored() throws IOException {
        Path folder = Files.createTempDirectory("pnlibrary-journal-seq");
        RuntimeJournal journal = new RuntimeJournal(folder.resolve("journal"), new PnLibraryConfig(), ownership, 0L, "/pn events");

        for (int i = 0; i < 300; i++) {
            journal.record(this, LogLevel.WARNING, "w " + i, null);
        }
        journal.close();

        Set<String> expected = new HashSet<>();
        for (int i = 0; i < 300; i++) {
            expected.add("w " + i);
        }
        Set<String> stored = new HashSet<>();
        for (JournalRecord record : journal.getStore().read(1_000)) {
            stored.add(record.getMessage());
        }
        assertEquals(expected, stored);
        assertEquals(0L, journal.summary().get("failedWrites"));
    }
}
