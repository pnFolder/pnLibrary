package org.pnfolder.pnlibrary.core.commands.modules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DiskUsageTest {
    @TempDir
    Path plugins;

    private void write(String relative, int size) throws IOException {
        Path file = plugins.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[size]);
    }

    @Test
    @DisplayName("splits a plugin folder by kind and adds the jar to the same plugin")
    void splitsAPluginFolderByKindAndAddsTheJarToTheSamePlugin() throws IOException {
        write("Shop-1.2.3.jar", 1000);
        write("Shop/config.yml", 10);
        write("Shop/players.db", 200);
        write("Shop/logs/latest.log", 30);
        write("Shop/lib/hikari.jar", 400);
        DiskReport report = DiskUsage.scan(plugins, Collections.<String>emptyList());
        assertEquals(1, report.getRows().size());
        DiskRow row = report.getRows().get(0);
        assertEquals("Shop", row.getName());
        assertEquals(new DiskRow("Shop", 1000, 10, 400, 30, 200), row);
        assertEquals(1640L, row.getTotal());
    }

    @Test
    @DisplayName("a jar without a folder is a plugin of its own and the biggest comes first")
    void aJarWithoutAFolderIsAPluginOfItsOwnAndTheBiggestComesFirst() throws IOException {
        write("Small-1.0.jar", 5);
        write("Big/data.bin", 500);
        DiskReport report = DiskUsage.scan(plugins, Collections.<String>emptyList());
        List<String> names = new ArrayList<String>();
        for (DiskRow row : report.getRows()) {
            names.add(row.getName());
        }
        assertEquals(Arrays.asList("Big", "Small"), names);
        assertEquals(505L, report.getTotal());
    }

    @Test
    @DisplayName("registered libraries count once even when listed twice, missing files count zero")
    void registeredLibrariesCountOnceEvenWhenListedTwiceMissingFilesCountZero() throws IOException {
        write("lib.jar", 70);
        String file = plugins.resolve("lib.jar").toString();
        DiskReport report = DiskUsage.scan(plugins, Arrays.asList(file, file, plugins.resolve("none.jar").toString()));
        assertEquals(70L, report.getRegisteredLibraries());
    }

    @Test
    @DisplayName("a missing plugins folder gives an empty report")
    void aMissingPluginsFolderGivesAnEmptyReport() {
        DiskReport report = DiskUsage.scan(plugins.resolve("nowhere"), Collections.<String>emptyList());
        assertTrue(report.getRows().isEmpty());
        assertEquals(-1L, report.getFree());
    }

    @Test
    @DisplayName("sizes read the same in the command and the menu")
    void sizesReadTheSameInTheCommandAndTheMenu() {
        assertEquals("12 Б", DiskUsage.format(12));
        assertEquals("320 КБ", DiskUsage.format(320 * 1024L));
        assertEquals("1.5 МБ", DiskUsage.format(1_572_864));
        assertEquals("2.00 ГБ", DiskUsage.format(2L * 1024 * 1024 * 1024));
        assertEquals("неизвестно", DiskUsage.format(-1));
    }
}
