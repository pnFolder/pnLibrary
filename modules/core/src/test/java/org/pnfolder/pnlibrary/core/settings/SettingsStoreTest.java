package org.pnfolder.pnlibrary.core.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SettingsStoreTest {
    @TempDir
    Path directory;

    private final List<String> warnings = new ArrayList<>();

    private Path file() {
        return directory.resolve("data/settings.json");
    }

    private SettingsStore store() {
        return new SettingsStore(file(), warnings::add);
    }

    private static Set<String> changedKeys(SettingsStore store) {
        Set<String> changed = new HashSet<>();
        for (SettingValue value : store.values()) {
            if (value.getChanged()) {
                changed.add(value.getSpec().getKey());
            }
        }
        return changed;
    }

    private JsonObject json() throws IOException {
        return JsonParser.parseString(new String(Files.readAllBytes(file()), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Test
    void aNewServerHasTheDefaultsAndNoFile() {
        SettingsStore store = store();

        assertEquals(new LibrarySettings(), store.current());
        assertFalse(Files.exists(file()));
        assertTrue(changedKeys(store).isEmpty());
        assertTrue(warnings.isEmpty());
    }

    @Test
    void aValueSurvivesARestartAndOnlyChangedValuesAreWritten() throws IOException {
        SettingsStore first = store();
        first.set("privacy", "true");
        first.set("updates.check-interval", "90m");
        first.set("downloads.automatic", "off");

        SettingsStore second = store();

        assertTrue(second.current().getPrivacy());
        assertEquals(Duration.ofMinutes(90), second.current().getUpdateCheckInterval());
        assertFalse(second.current().getDownloadsAutomatic());
        Set<String> expected = new HashSet<>(
            Arrays.asList("privacy", "updates.check-interval", "downloads.automatic"));
        assertEquals(expected, changedKeys(second));
        JsonObject json = json();
        assertEquals(expected, json.keySet());
        assertTrue(json.get("privacy").getAsJsonPrimitive().isBoolean());
        assertEquals("1ч30м", json.get("updates.check-interval").getAsString());
        assertTrue(warnings.isEmpty());
    }

    @Test
    void resetReturnsASettingToItsDefaultAndRemovesItFromTheFile() throws IOException {
        SettingsStore store = store();
        store.set("history-retention-days", "90");

        SettingChange change = store.reset("history-retention-days");

        assertEquals("90", change.getBefore());
        assertEquals("30", change.getAfter());
        assertEquals(30, store.current().getHistoryRetentionDays());
        assertFalse(json().has("history-retention-days"));
    }

    @Test
    void anInvalidValueIsRefusedAndNothingChanges() {
        SettingsStore store = store();
        store.set("history-retention-days", "60");

        assertThrows(SettingException.class, () -> store.set("history-retention-days", "7"));
        assertThrows(SettingException.class, () -> store.set("no-such-key", "1"));
        assertThrows(SettingException.class, () -> store.reset("no-such-key"));

        assertEquals(60, store().current().getHistoryRetentionDays());
    }

    @Test
    void retiredSettingsInTheFileAreIgnoredWithoutAWarning() throws IOException {
        Files.createDirectories(file().getParent());
        Files.write(file(), ("{\"updates.read-timeout\": \"5m\", \"downloads.destinations.cache\": false, "
            + "\"privacy\": true}").getBytes(StandardCharsets.UTF_8));

        SettingsStore store = store();

        assertTrue(store.current().getPrivacy());
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    void invalidValuesInTheFileFallBackToTheDefaultsWithAWarning() throws IOException {
        Files.createDirectories(file().getParent());
        Files.write(file(), ("{\"history-retention-days\": 7, \"privacy\": \"maybe\", \"time-zone\": \"Mars/Base\", "
            + "\"mystery\": 1, \"journal-foreign-plugins\": true, \"downloads.enabled\": [1]}")
            .getBytes(StandardCharsets.UTF_8));

        SettingsStore store = store();

        assertEquals(30, store.current().getHistoryRetentionDays());
        assertFalse(store.current().getPrivacy());
        assertEquals(new LibrarySettings().getTimeZone(), store.current().getTimeZone());
        assertTrue(store.current().getDownloadsEnabled());
        assertTrue(store.current().getJournalForeignPlugins(), "a valid value next to broken ones is kept");
        assertEquals(5, warnings.size(), warnings.toString());
        assertTrue(anyContains("history-retention-days"));
        assertTrue(anyContains("mystery"));
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
    void aFileThatIsNotJsonIsMovedAsideAndTheDefaultsAreUsed() throws IOException {
        Files.createDirectories(file().getParent());
        Files.write(file(), "{ not json".getBytes(StandardCharsets.UTF_8));

        SettingsStore store = store();

        assertEquals(new LibrarySettings(), store.current());
        assertTrue(Files.exists(directory.resolve("data/settings.json.broken")));
        assertEquals(1, warnings.size());
        store.set("privacy", "true");
        assertTrue(store().current().getPrivacy());
    }

    @Test
    void listenersHearAboutAChangeAndTheConfigFollowsIt() {
        SettingsStore store = store();
        List<String> heard = new ArrayList<>();
        store.onChange(settings -> heard.add(settings.getTimeZone()));

        store.set("time-zone", "Europe/Moscow");

        assertEquals(Arrays.asList("Europe/Moscow"), heard);
        assertEquals("Europe/Moscow", store.config().getTimeZone());
        store.set("privacy", "true");
        assertTrue(store.config().isPrivacy());
    }

    @Test
    void aFailingListenerDoesNotUndoASavedChange() {
        SettingsStore store = store();
        store.onChange(settings -> {
            throw new IllegalStateException("boom");
        });

        store.set("privacy", "true");

        assertTrue(store.current().getPrivacy());
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("privacy"));
    }
}
