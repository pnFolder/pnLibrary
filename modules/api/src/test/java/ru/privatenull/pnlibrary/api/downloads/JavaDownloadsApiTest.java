package ru.privatenull.pnlibrary.api.downloads;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JavaDownloadsApiTest {
    @Test
    void declaresFilesFromJava() {
        FileDownloads downloads = FileDownloads.builder()
            .file("cases-data", file -> file
                .url("https://example.org/cases.bin")
                .destination(DownloadDestination.DATA_FOLDER, "resources/cases.bin"))
            .build();

        assertEquals(1, downloads.getFiles().size());
    }

    @Test
    void rejectsEscapingDestination() {
        assertThrows(IllegalArgumentException.class, () -> FileDownloads.builder()
            .file("escape", file -> file.url("https://example.org/a.bin")
                .destination(DownloadDestination.DATA_FOLDER, "../outside.bin"))
            .build());
    }
}
