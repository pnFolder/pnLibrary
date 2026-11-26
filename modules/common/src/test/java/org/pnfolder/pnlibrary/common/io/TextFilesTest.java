package org.pnfolder.pnlibrary.common.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TextFilesTest {
    @TempDir
    Path directory;

    @Test
    void roundTripsUnicodeAndHonorsAppendOptions() throws IOException {
        Path file = directory.resolve("text.txt");
        assertEquals(file, TextFiles.write(file, "Привет 🌍"));
        TextFiles.write(file, "!", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        assertEquals("Привет 🌍!", TextFiles.read(file));
    }

    @Test
    void invalidTextIsRejectedBeforeAnExistingFileIsReplaced() throws IOException {
        Path file = directory.resolve("text.txt");
        TextFiles.write(file, "original");
        assertThrows(MalformedInputException.class, () -> TextFiles.write(file, "\uD800"));
        assertEquals("original", TextFiles.read(file));
    }

    @Test
    void malformedUtf8IsRejectedWhenReading() throws IOException {
        Path file = directory.resolve("text.txt");
        Files.write(file, new byte[] {(byte) 0xc3, 0x28});
        assertThrows(MalformedInputException.class, () -> TextFiles.read(file));
    }
}
