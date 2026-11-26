package org.pnfolder.pnlibrary.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pnfolder.pnlibrary.api.config.ConfigRoot;
import org.pnfolder.pnlibrary.api.plugin.PluginId;

class ConfigPathsTest {
    @TempDir
    Path root;

    private ConfigPaths paths() throws IOException {
        Path folder = root.resolve("plugins").resolve("shop");
        Files.createDirectories(folder);
        return new ConfigPaths(folder, PluginId.of("shop"));
    }

    @Test
    void aPathInsideThePluginFolderIsAccepted() throws IOException {
        Path target = paths().checked("data/prices.yml", Paths.get("data/prices.yml"), ConfigRoot.PLUGIN);

        assertEquals(root.resolve("plugins").resolve("shop").resolve("data").resolve("prices.yml"), target);
    }

    @Test
    void aPathThatClimbsOutOfTheFolderIsRefused() throws IOException {
        ConfigPaths paths = paths();
        assertThrows(IllegalArgumentException.class,
                () -> paths.checked("../other/x.yml", Paths.get("../other/x.yml"), ConfigRoot.PLUGIN));
    }

    @Test
    void onlyYamlFilesAreAccepted() throws IOException {
        ConfigPaths paths = paths();
        assertThrows(IllegalArgumentException.class,
                () -> paths.checked("prices.txt", Paths.get("prices.txt"), ConfigRoot.PLUGIN));
    }

    @Test
    void thePluginsFolderIsTheParentOfThePluginFolder() throws IOException {
        assertEquals(root.resolve("plugins"), paths().rootDirectory(ConfigRoot.PLUGINS));
    }
}
