package org.pnfolder.pnlibrary.core.observability.report;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupportReportStoreTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void storesTheRequestedReportRepresentation() throws IOException {
        SupportReportStore store = new SupportReportStore(temporaryDirectory);
        byte[] payload = {1, 2, 3};

        Path encrypted = store.save(payload, true, 2);

        assertTrue(encrypted.getFileName().toString().endsWith(".pnsupport"));
        assertArrayEquals(payload, Files.readAllBytes(encrypted));
    }

    @Test
    void retentionRemovesOnlyOldReportsOwnedByTheStore() throws IOException {
        SupportReportStore store = new SupportReportStore(temporaryDirectory);
        Path unrelated = temporaryDirectory.resolve("operator-notes.txt");
        Files.write(unrelated, "keep".getBytes());
        Path first = store.save(new byte[]{1}, false, 2);
        Files.setLastModifiedTime(first, FileTime.fromMillis(1));
        Path second = store.save(new byte[]{2}, false, 2);
        Files.setLastModifiedTime(second, FileTime.fromMillis(2));

        Path newest = store.save(new byte[]{3}, false, 2);
        List<Path> ownedReports;
        try (Stream<Path> stream = Files.list(temporaryDirectory)) {
            ownedReports = stream.filter(path -> path.getFileName().toString().startsWith("report-"))
                    .collect(Collectors.toList());
        }

        assertEquals(2, ownedReports.size());
        assertTrue(Files.exists(second));
        assertTrue(Files.exists(newest));
        assertTrue(Files.exists(unrelated));
    }
}
