package org.pnfolder.pnlibrary.config.yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigurationFileStoreTest {
    @TempDir
    Path directory;

    private static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    @Test
    void atomicWriteNormalizesLineEndingsAndValidatesBeforeReplacement() throws IOException {
        File file = directory.resolve("config.yml").toFile();
        Files.write(file.toPath(), "value: old\n".getBytes(StandardCharsets.UTF_8));
        ConfigurationFileStore store = new ConfigurationFileStore(file, content -> {
            if (content.contains("invalid")) {
                throw new IllegalArgumentException("Failed requirement.");
            }
        });

        store.write("value: new\r\n");
        assertEquals("value: new\n", read(file));

        try {
            store.write("invalid\n");
        } catch (Exception expected) {
            // the failed write must leave the file as it was
        }
        assertEquals("value: new\n", read(file));
        File[] left = directory.toFile().listFiles();
        for (File candidate : left == null ? new File[0] : left) {
            assertFalse(candidate.getName().endsWith(".tmp"));
        }
    }

    @Test
    void backupRetentionKeepsOnlyTheFiveNewestFiles() throws Exception {
        File file = directory.resolve("config.yml").toFile();
        ConfigurationFileStore store = new ConfigurationFileStore(file, content -> { });

        for (int index = 0; index < 7; index++) {
            store.backup("revision-" + index);
            Thread.sleep(2);
        }

        List<Path> backups = new ArrayList<Path>();
        try (Stream<Path> paths = Files.list(directory)) {
            paths.filter(path -> path.getFileName().toString().endsWith(".bak")).forEach(backups::add);
        }
        assertEquals(5, backups.size());
        for (Path backup : backups) {
            assertTrue(new String(Files.readAllBytes(backup), StandardCharsets.UTF_8).startsWith("revision-"));
        }
    }
}
