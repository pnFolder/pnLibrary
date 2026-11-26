package org.pnfolder.pnlibrary.core.diagnostics;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pnfolder.pnlibrary.core.diagnostics.config.SecureConfigReader;

class SecureConfigReaderTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void readsARegularFileWithinTheTrustedRoot() throws IOException {
        byte[] expected = "enabled: true\n".getBytes(StandardCharsets.UTF_8);
        Files.write(temporaryDirectory.resolve("config.yml"), expected);

        SecureConfigReader.Result result = new SecureConfigReader().read(temporaryDirectory, "config.yml");

        assertArrayEquals(expected, result.getContent());
        assertNull(result.getError());
    }

    @Test
    void rejectsTraversalAndForbiddenExtensions() throws IOException {
        SecureConfigReader reader = new SecureConfigReader();
        Files.write(temporaryDirectory.resolve("cache.db"), new byte[] {1});

        SecureConfigReader.Result traversal = reader.read(temporaryDirectory, "../outside.yml");
        SecureConfigReader.Result forbidden = reader.read(temporaryDirectory, "cache.db");

        assertEquals("[SECURITY: path traversal blocked]", traversal.getError());
        assertEquals("[SECURITY: binary or database file extension blocked]", forbidden.getError());
    }

    @Test
    void rejectsFilesLargerThanTheConfiguredByteBudget() throws IOException {
        Files.write(temporaryDirectory.resolve("large.yml"), new byte[5]);

        SecureConfigReader.Result result = new SecureConfigReader(4).read(temporaryDirectory, "large.yml");

        assertEquals("[file exceeds size limit of 4 bytes (5 bytes)]", result.getError());
        assertNull(result.getContent());
    }
}
