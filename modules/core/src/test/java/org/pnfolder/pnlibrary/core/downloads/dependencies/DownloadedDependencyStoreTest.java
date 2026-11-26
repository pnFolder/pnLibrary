package org.pnfolder.pnlibrary.core.downloads.dependencies;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DownloadedDependencyStoreTest {
    @TempDir
    Path directory;

    @Test
    void recordsDependenciesAndConsumesThoseFoundAfterRestart() throws IOException {
        DownloadedDependencyStore store = new DownloadedDependencyStore(directory);

        store.record(Arrays.asList("Vault", "LuckPerms", "vault"));

        assertEquals(Arrays.asList("LuckPerms", "Vault"), Files.readAllLines(store.getMarker()));
        assertEquals(
            Arrays.asList(new DownloadedDependency("vault", "1.7.3")),
            store.consumeInstalled(Collections.singletonMap("vault", "1.7.3")));
        assertEquals(Arrays.asList("LuckPerms"), Files.readAllLines(store.getMarker()));

        assertEquals(
            Arrays.asList(new DownloadedDependency("LuckPerms", "5.4.0")),
            store.consumeInstalled(Collections.singletonMap("LuckPerms", "5.4.0")));
        assertFalse(Files.exists(store.getMarker()));
    }
}
