package org.pnfolder.pnlibrary.common.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TsvRecordsTest {
    @TempDir
    Path directory;

    @Test
    void recordsComeBackWithTabsLineBreaksAndBackslashesIntact() {
        Path file = directory.resolve("nested").resolve("table.tsv");
        List<List<String>> records = Arrays.asList(
            Arrays.asList("a\tb", "line\none", "x\\y"),
            Arrays.asList("", null, "plain"));

        assertTrue(TsvRecords.write(file, "#table 1", records));

        assertEquals(
            Arrays.asList(Arrays.asList("a\tb", "line\none", "x\\y"), Arrays.asList("", "", "plain")),
            TsvRecords.read(file));
    }

    @Test
    void aMissingFileReadsAsNoRecordsAndTheHeaderIsNotARecord() throws IOException {
        Path file = directory.resolve("table.tsv");
        assertEquals(Collections.emptyList(), TsvRecords.read(file));

        TsvRecords.write(file, "#table 1", Collections.emptyList());

        assertEquals(Collections.emptyList(), TsvRecords.read(file));
        assertEquals("#table 1\n", new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
    }
}
