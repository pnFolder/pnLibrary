package org.pnfolder.pnlibrary.core.metrics.bstats;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BStatsSettingsTest {
    @TempDir
    Path directory;

    @BeforeEach
    void skipRelocationCheck() {
        System.setProperty("bstats.relocatecheck", "false");
    }

    @AfterEach
    void restoreRelocationCheck() {
        System.clearProperty("bstats.relocatecheck");
    }

    private File file(String name) {
        return directory.resolve("bStats").resolve(name).toFile();
    }

    private static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    @Test
    void aMissingYamlFileIsCreatedWithAnIdentifierAndSendingEnabled() throws IOException {
        File file = file("config.yml");

        BStatsSettings first = BStatsSettings.yaml(file);
        BStatsSettings second = BStatsSettings.yaml(file);

        assertTrue(first.isEnabled());
        assertTrue(first.isCreatedNow());
        assertFalse(second.isCreatedNow());
        assertEquals(first.getServerUuid(), second.getServerUuid());
        assertTrue(read(file).contains("serverUuid: \"" + first.getServerUuid() + "\""));
    }

    @Test
    void theOptOutAndLoggingFlagsOfAFileWrittenByAnotherPluginAreKept() throws IOException {
        File file = file("config.yml");
        file.getParentFile().mkdirs();
        Files.write(file.toPath(), ("# bStats (https://bStats.org) collects some basic information for plugin authors\n"
                + "enabled: false\n"
                + "serverUuid: 0b1b8f2e-5c1e-4e4e-9d2a-1f6f7a1a0001\n"
                + "logFailedRequests: true\n"
                + "logSentData: 'true'\n"
                + "logResponseStatusText: false").getBytes(StandardCharsets.UTF_8));

        BStatsSettings settings = BStatsSettings.yaml(file);

        assertFalse(settings.isEnabled());
        assertEquals("0b1b8f2e-5c1e-4e4e-9d2a-1f6f7a1a0001", settings.getServerUuid());
        assertTrue(settings.isLogFailedRequests());
        assertTrue(settings.isLogSentData());
        assertFalse(settings.isLogResponseStatusText());
        assertFalse(settings.isCreatedNow());
    }

    @Test
    void aFileWithoutAnIdentifierGetsOneAndKeepsTheOptOut() throws IOException {
        File file = file("config.yml");
        file.getParentFile().mkdirs();
        Files.write(file.toPath(), "enabled: false\n".getBytes(StandardCharsets.UTF_8));

        BStatsSettings settings = BStatsSettings.yaml(file);

        assertFalse(settings.isEnabled());
        assertFalse(settings.getServerUuid().trim().isEmpty());
        assertTrue(read(file).contains("serverUuid: \"" + settings.getServerUuid() + "\""));
        assertTrue(read(file).contains("enabled: false"));
    }

    @Test
    void thePropertiesFileOfVelocityIsCreatedAndReadAgain() throws IOException {
        File file = file("config.txt");

        BStatsSettings first = BStatsSettings.properties(file);
        BStatsSettings second = BStatsSettings.properties(file);

        assertTrue(first.isEnabled());
        assertTrue(first.isCreatedNow());
        assertFalse(second.isCreatedNow());
        assertEquals(first.getServerUuid(), second.getServerUuid());
        assertTrue(read(file).contains("server-uuid=" + first.getServerUuid()));
    }

    @Test
    void disabledSettingsSendNothingAndHaveTheirOwnIdentifier() {
        BStatsSettings one = BStatsSettings.disabled();

        assertFalse(one.isEnabled());
        assertNotEquals(one.getServerUuid(), BStatsSettings.disabled().getServerUuid());
    }

    @Test
    void theClientStartsAndStopsWithoutSendingWhileSendingIsOff() {
        BStatsClient client = new BStatsClient("bukkit", 34864, BStatsSettings.disabled(), "1.0",
                builder -> { }, (message, error) -> { }, message -> { });

        client.addCustomChart(new org.bstats.charts.SimplePie("rank", () -> "vip"));
        client.shutdown();
    }

    @Test
    void aFileThatCannotBeReadCountsAsAnOptOut() {
        File unreadable = directory.resolve("config.yml").toFile();
        unreadable.mkdirs();

        assertFalse(BStatsSettings.yaml(unreadable).isEnabled());
    }
}
