package org.pnfolder.pnlibrary.core.runtime.details;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.logging.LogLevel;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryConfig;
import org.pnfolder.pnlibrary.spi.platform.PlatformOwnership;

class RuntimeJournalTest {
    private final PlatformOwnership ownership = new PlatformOwnership() {
        @Override
        public @NotNull Map<String, String> ownerDetails(@NotNull Object owner) {
            return Collections.singletonMap("id", "plugin");
        }
    };

    @Test
    void aFailedJournalWriteIsReportedOnceInsteadOfBeingLost() throws IOException, InterruptedException {
        Path folder = Files.createTempDirectory("pnlibrary-journal-test");
        Files.write(folder.resolve("journal"), "a file where the journal folder should be".getBytes(StandardCharsets.UTF_8));
        List<Throwable> reported = new ArrayList<>();
        CountDownLatch first = new CountDownLatch(1);
        RuntimeJournal journal = new RuntimeJournal(folder.resolve("journal"), new PnLibraryConfig(), ownership, 0L, "/pn events", failure -> {
            reported.add(failure);
            first.countDown();
        });

        journal.record(this, LogLevel.ERROR, "one", null);
        assertTrue(first.await(5, TimeUnit.SECONDS));
        journal.record(this, LogLevel.ERROR, "two", null);
        journal.close();

        assertEquals(1, reported.size());
        assertEquals(2L, journal.summary().get("failedWrites"));
    }
}
