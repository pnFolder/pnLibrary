package org.pnfolder.pnlibrary.core.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pnfolder.pnlibrary.api.downloads.source.DownloadDestination;
import org.pnfolder.pnlibrary.core.downloads.DownloadConfiguration;
import org.pnfolder.pnlibrary.updates.paths.LibraryPaths;

class LegacySettingsImportTest {
    @TempDir
    Path directory;

    private final List<String> warnings = new ArrayList<>();
    private LibraryPaths paths;

    private LibraryPaths paths() {
        if (paths == null) {
            paths = new LibraryPaths(directory.resolve("plugins/pnLibrary"));
        }
        return paths;
    }

    private static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, text.getBytes(StandardCharsets.UTF_8));
    }

    private SettingsStore importAll() {
        SettingsStore store = new SettingsStore(paths().getSettings(), warnings::add);
        new LegacySettingsImport(paths(), warnings::add).run(store);
        return store;
    }

    private boolean anyContains(String text) {
        for (String warning : warnings) {
            if (warning.contains(text)) {
                return true;
            }
        }
        return false;
    }

    @Test
    void theValuesOfTheOldFilesAreTakenOverOnceAndTheFilesAreRenamed() throws IOException {
        write(paths().getLegacyConfig(), "# header\nprivacy: true\nhistory-retention-days: 60\n"
            + "time-zone: Europe/Moscow\nno-permission-message: Нельзя\njournal-foreign-plugins: false\n");
        write(paths().getLegacyDownloadsConfig(),
            "enabled: true\nautomatic: false\ndestinations:\n  data-folder: true\n  cache: false\n");

        SettingsStore store = importAll();

        LibrarySettings settings = store.current();
        assertTrue(settings.getPrivacy());
        assertEquals(60, settings.getHistoryRetentionDays());
        assertEquals("Europe/Moscow", settings.getTimeZone());
        assertEquals(new HashSet<>(Arrays.asList(DownloadDestination.DATA_FOLDER, DownloadDestination.CACHE)),
            DownloadConfiguration.of(settings).getDestinations());
        assertFalse(Files.exists(paths().getLegacyConfig()));
        assertFalse(Files.exists(paths().getLegacyDownloadsConfig()));
        assertTrue(Files.exists(paths().getLegacyConfig().resolveSibling("config.yml.old")));
        assertTrue(Files.exists(paths().getLegacyDownloadsConfig().resolveSibling("downloads.yml.old")));
        Set<String> changed = new HashSet<>();
        for (SettingValue value : store.values()) {
            if (value.getChanged()) {
                changed.add(value.getSpec().getKey());
            }
        }
        assertEquals(new HashSet<>(Arrays.asList("privacy", "history-retention-days", "time-zone")), changed);

        SettingsStore again = new SettingsStore(paths().getSettings());
        new LegacySettingsImport(paths()).run(again);
        assertEquals(settings, again.current(), "a second start changes nothing");
    }

    @Test
    void theOldDownloadChoiceMeansNothingAnyMoreAndIsRemoved() throws IOException {
        write(paths().getLegacyDownloadsConfig(), "automatic: true\n");
        write(paths().getDownloads().resolve("preferences.state"), "automatic=false\n");

        SettingsStore store = importAll();

        assertEquals(new LibrarySettings(), store.current());
        assertFalse(Files.exists(paths().getDownloads().resolve("preferences.state")));
    }

    @Test
    void whatWasAlreadySetByCommandIsNotOverwrittenByAnOldFile() throws IOException {
        new SettingsStore(paths().getSettings()).set("history-retention-days", "120");
        write(paths().getLegacyConfig(), "history-retention-days: 45\nprivacy: true\n");

        SettingsStore store = importAll();

        assertEquals(120, store.current().getHistoryRetentionDays());
        assertTrue(store.current().getPrivacy());
    }

    @Test
    void anInvalidOrRemovedKeyIsReportedAndTheRestIsStillImported() throws IOException {
        write(paths().getLegacyConfig(), "history-retention-days: 7\nexcluded-paths:\n  - plugins\nprivacy: true\n");

        SettingsStore store = importAll();

        assertEquals(30, store.current().getHistoryRetentionDays());
        assertTrue(store.current().getPrivacy());
        assertTrue(anyContains("history-retention-days"));
        assertTrue(anyContains("excluded-paths"));
    }

    @Test
    void aFileThatIsNotYamlStaysWhereItIs() throws IOException {
        write(paths().getLegacyConfig(), "privacy: [unclosed\n");

        SettingsStore store = importAll();

        assertEquals(new LibrarySettings(), store.current());
        assertTrue(Files.exists(paths().getLegacyConfig()));
        assertTrue(anyContains("config.yml"));
    }

    @Test
    void withoutOldFilesNothingHappens() {
        SettingsStore store = importAll();

        assertEquals(new LibrarySettings(), store.current());
        assertFalse(Files.exists(paths().getSettings()));
        assertTrue(warnings.isEmpty());
    }
}
