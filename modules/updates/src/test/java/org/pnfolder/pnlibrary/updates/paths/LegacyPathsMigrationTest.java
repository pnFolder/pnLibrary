package org.pnfolder.pnlibrary.updates.paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pnfolder.pnlibrary.api.updates.product.ProductId;
import org.pnfolder.pnlibrary.api.updates.product.UpdateChannel;
import org.pnfolder.pnlibrary.updates.engine.FreezeStore;
import org.pnfolder.pnlibrary.updates.engine.UpdatePreferencesStore;
import org.pnfolder.pnlibrary.updates.policy.UpdatePolicyOverrides;

class LegacyPathsMigrationTest {
    @TempDir
    Path directory;

    private final List<String> warnings = new ArrayList<>();

    private Path plugin() {
        return directory.resolve("plugins/pnLibrary");
    }

    private LibraryPaths paths() {
        return new LibraryPaths(plugin());
    }

    private void write(String relative, String text) throws IOException {
        Path file = plugin().resolve(relative);
        Files.createDirectories(file.getParent());
        Files.write(file, text.getBytes(StandardCharsets.UTF_8));
    }

    private static String read(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    private void migrate() {
        new LegacyPathsMigration(paths(), warnings::add).run();
    }

    @Test
    void oldStateIsMovedIntoDataAndTheOldPlacesAreGone() throws IOException {
        write("updates/components.json", "components");
        write("updates/transactions/one/journal.json", "transaction");
        write("journal/day.log", "journal");
        write("reports/report.zip", "report");
        write("observability/observations.jsonl", "observations");
        write("diagnostics/history/incident.json", "incident");
        write("task-history.tsv", "tasks");
        write("event-history.tsv", "events");
        write("upload-ledger.json", "ledger");
        write("downloads/pins.state", "pins");
        write("config.yml", "keep");

        migrate();

        LibraryPaths paths = paths();
        assertEquals("components", read(paths.getComponents()));
        assertEquals("transaction", read(paths.getTransactions().resolve("one/journal.json")));
        assertEquals("journal", read(paths.getJournal().resolve("day.log")));
        assertEquals("report", read(paths.getReports().resolve("report.zip")));
        assertEquals("observations", read(paths.getObservations()));
        assertEquals("incident", read(paths.getDiagnosticsHistory().resolve("incident.json")));
        assertEquals("tasks", read(paths.getTaskHistory()));
        assertEquals("events", read(paths.getEventHistory()));
        assertEquals("ledger", read(paths.getUploadLedger()));
        assertEquals("pins", read(paths.getDownloads().resolve("pins.state")));
        for (String old : Arrays.asList("updates", "journal", "reports", "observability", "diagnostics",
            "task-history.tsv", "event-history.tsv", "upload-ledger.json", "downloads")) {
            assertFalse(Files.exists(plugin().resolve(old)), old);
        }
        assertEquals("keep", read(paths.getLegacyConfig()));
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    void anExistingNewLocationIsNeverOverwritten() throws IOException {
        write("task-history.tsv", "old");
        write("data/history/task-history.tsv", "new");
        write("updates/components.json", "old");
        write("data/updates/components.json", "new");
        write("updates/catalog/cache.json", "old catalog");

        migrate();

        LibraryPaths paths = paths();
        assertEquals("new", read(paths.getTaskHistory()));
        assertEquals("old", read(plugin().resolve("task-history.tsv")));
        assertEquals("new", read(paths.getComponents()));
        assertEquals("old", read(plugin().resolve("updates/components.json")));
        assertEquals("old catalog", read(paths.getCatalog().resolve("cache.json")));
    }

    @Test
    void aFreshInstallAndARepeatedRunChangeNothing() throws IOException {
        migrate();
        assertFalse(Files.exists(paths().getData()));
        write("task-history.tsv", "tasks");
        migrate();
        migrate();
        assertEquals("tasks", read(paths().getTaskHistory()));
        assertTrue(warnings.isEmpty());
    }

    @Test
    void theTwoOldSettingsFilesBecomeOnePreferencesFileAndAreRemoved() throws IOException {
        write("updates/settings.json", "{\"allPlugins\":true,\"channels\":{\"shop\":\"BETA\"},"
            + "\"automatic\":{\"shop\":false}}");
        write("updates/freezes.json", "{\"shop\":" + Instant.parse("2026-09-17T12:00:00Z").toEpochMilli() + "}");

        migrate();

        LibraryPaths paths = paths();
        assertFalse(Files.exists(paths.getUpdates().resolve("settings.json")));
        assertFalse(Files.exists(paths.getUpdates().resolve("freezes.json")));
        UpdatePreferencesStore store = new UpdatePreferencesStore(paths.getPreferences());
        UpdatePolicyOverrides overrides = new UpdatePolicyOverrides(store);
        assertEquals(UpdateChannel.BETA, overrides.channel("shop"));
        Clock clock = Clock.fixed(Instant.parse("2026-09-17T10:00:00Z"), ZoneOffset.UTC);
        assertEquals(Collections.singleton(ProductId.of("shop")), new FreezeStore(store, clock).active().keySet());
    }

    @Test
    void anUnreadableOldSettingsFileIsKeptAndTheOtherOneIsStillMerged() throws IOException {
        write("updates/settings.json", "{ broken");
        write("updates/freezes.json", "{\"shop\":4102444800000}");

        migrate();

        LibraryPaths paths = paths();
        assertTrue(Files.exists(paths.getUpdates().resolve("settings.json")));
        assertFalse(Files.exists(paths.getUpdates().resolve("freezes.json")));
        assertEquals(Collections.singletonMap("shop", 4102444800000L),
            new UpdatePreferencesStore(paths.getPreferences()).current().getFreezes());
        assertEquals(1, warnings.size(), warnings.toString());
    }

    @Test
    void oldSettingsDoNotReplaceAPreferencesFileThatAlreadyExists() throws IOException {
        write("data/updates/preferences.json", "{\"allPlugins\":false}");
        write("data/updates/settings.json", "{\"allPlugins\":true}");

        migrate();

        assertEquals(Boolean.FALSE, new UpdatePreferencesStore(paths().getPreferences()).current().getAllPlugins());
    }
}
