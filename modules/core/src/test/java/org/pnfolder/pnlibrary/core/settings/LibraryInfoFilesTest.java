package org.pnfolder.pnlibrary.core.settings;

import org.pnfolder.pnlibrary.core.guide.LibraryCommandGuide;
import org.pnfolder.pnlibrary.core.guide.GuideEntry;
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
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity;
import org.pnfolder.pnlibrary.updates.paths.LibraryPaths;

class LibraryInfoFilesTest {
    @TempDir
    Path directory;

    private final List<String> warnings = new ArrayList<>();
    private SettingsStore store;

    private Path plugin() {
        return directory.resolve("plugins/pnLibrary");
    }

    private LibraryPaths paths() {
        return new LibraryPaths(plugin());
    }

    private SettingsStore store() {
        if (store == null) {
            store = new SettingsStore(paths().getSettings());
        }
        return store;
    }

    private LibraryInfoFiles files() {
        return files("2026-11-15T12:30:00Z", paths());
    }

    private LibraryInfoFiles files(String now, LibraryPaths pluginPaths) {
        return new LibraryInfoFiles(pluginPaths, store()::values, "1.2.3",
            Clock.fixed(Instant.parse(now), ZoneOffset.UTC), warnings::add);
    }

    private String read(String name) throws IOException {
        return new String(Files.readAllBytes(plugin().resolve(name)), StandardCharsets.UTF_8);
    }

    private static String stripEnd(String text) {
        int end = text.length();
        while (end > 0 && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(0, end);
    }

    private static List<String> lines(String text) {
        return Arrays.asList(text.split("\r\n|\r|\n", -1));
    }

    @Test
    void theHelpCarriesTheCurrentLinksTheCommandsTheVersionAndTheTime() {
        String text = files().helpText();

        assertTrue(text.contains(PnLibraryIdentity.SUPPORT_URL));
        assertTrue(text.contains(PnLibraryIdentity.REPOSITORY_URL));
        assertTrue(text.contains(PnLibraryIdentity.REPOSITORY_URL + "/tree/main/docs"));
        for (String part : Arrays.asList("/pn settings", "/pn debug", "/pn updates", "заказать", "data/", "Команды.txt",
            "Настройки.txt")) {
            assertTrue(text.contains(part), part);
        }
        assertTrue(text.contains("Этот файл создаётся заново при каждом запуске, ваши правки не сохранятся."));
        assertTrue(stripEnd(text).endsWith(PnLibraryIdentity.NAME + " 1.2.3\nСоздан: 2026-11-15 12:30 UTC"), text);
    }

    @Test
    void theLinksAreTheOnesGivenNotRememberedOnes() {
        String text = new LibraryInfoFiles(paths(), store()::values, "1", "https://example.org/help",
            "https://example.org/code").helpText();

        assertTrue(text.contains("https://example.org/help"));
        assertTrue(text.contains("https://example.org/code/tree/main/docs"));
    }

    @Test
    void theOverviewListsEverySettingWithItsValueDefaultLimitsAndDescription() {
        String text = files().settingsText();

        for (SettingSpec spec : SettingsRegistry.getAll()) {
            assertTrue(text.contains(spec.getKey()), spec.getKey());
            assertTrue(text.contains(spec.getTitle()), spec.getKey());
            assertTrue(text.contains(spec.getDescription()), spec.getKey());
            assertTrue(text.contains(spec.display(spec.getDefault())), spec.getKey());
            if (!spec.range().isEmpty()) {
                assertTrue(text.contains(spec.range()), spec.getKey());
            }
        }
        assertTrue(text.contains("/pn settings set"));
        assertTrue(text.contains("Этот файл создаётся заново"));
    }

    @Test
    void theOverviewShowsTheNewValueAfterAChange() throws IOException {
        LibraryInfoFiles files = files();
        files.refresh();
        assertTrue(read("Настройки.txt").contains("Сейчас: 30  (по умолчанию)"));

        store().onChange(settings -> files.refreshSettings());
        store().set("history-retention-days", "120");

        assertTrue(read("Настройки.txt").contains("Сейчас: 120  (изменено администратором)"), read("Настройки.txt"));
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    void theHelpKeepsItsListOfUsefulCommandsShort() {
        List<String> lines = lines(files().helpText());
        int start = lines.indexOf("Самые полезные команды");

        assertTrue(start >= 0);
        int count = 0;
        for (String line : lines.subList(start + 1, lines.size())) {
            if (!line.startsWith("  /pn")) {
                break;
            }
            count++;
        }
        assertTrue(count <= 8, lines.toString());
    }

    @Test
    void theCommandFileHasTheHeaderEveryPermissionAndTheCreationTime() {
        String text = files().commandsText();

        assertTrue(text.contains(
            "Все права по умолчанию у операторов; назначайте их в LuckPerms или другом менеджере прав."));
        assertTrue(text.contains("Этот файл создаётся заново при каждом запуске, ваши правки не сохранятся."));
        for (GuideEntry entry : LibraryCommandGuide.getEntries()) {
            assertTrue(text.contains(entry.getUsage()), entry.getUsage());
            if (entry.getPermission() != null) {
                assertTrue(text.contains(entry.getPermission()), entry.getPermission());
            }
        }
        assertTrue(stripEnd(text).endsWith(PnLibraryIdentity.NAME + " 1.2.3\nСоздан: 2026-11-15 12:30 UTC"), text);
    }

    @Test
    void theThreeFilesAreWrittenAsUtf8UnderTheirRussianNames() throws IOException {
        files().refresh();

        assertTrue(Files.isRegularFile(plugin().resolve("Команды.txt")));
        assertFalse(Files.exists(plugin().resolve("COMMANDS.txt")));
        assertFalse(read("Команды.txt").indexOf('\r') >= 0);

        assertTrue(Files.isRegularFile(plugin().resolve("Помощь.txt")));
        assertTrue(Files.isRegularFile(plugin().resolve("Настройки.txt")));
        assertFalse(Files.exists(plugin().resolve("HELP.txt")));
        assertFalse(read("Помощь.txt").indexOf('\r') >= 0);
        assertEquals("Помощь pnLibrary", lines(read("Помощь.txt")).get(0));
    }

    @Test
    void aStartThatChangesNothingLeavesTheFilesAloneOnlyTheTimeLineIsIgnored() throws IOException {
        files("2026-11-15T12:30:00Z", paths()).refresh();
        String before = read("Помощь.txt");

        files("2026-11-16T08:00:00Z", paths()).refresh();

        assertEquals(before, read("Помощь.txt"));
        assertTrue(read("Команды.txt").contains("Все права по умолчанию у операторов"));
    }

    @Test
    void aNewVersionRewritesTheFiles() throws IOException {
        files().refresh();
        new LibraryInfoFiles(paths(), store()::values, "9.9.9").refresh();

        assertTrue(read("Помощь.txt").contains(PnLibraryIdentity.NAME + " 9.9.9"));
    }

    @Test
    void whenTheRussianNameCannotBeUsedTheAsciiNamesAreWritten() throws IOException {
        // A folder in the way makes the Russian name unusable, as an unmappable name does on a non-UTF-8 JVM.
        Files.createDirectories(plugin().resolve("Помощь.txt/inside"));
        Files.createDirectories(plugin().resolve("Настройки.txt/inside"));
        Files.createDirectories(plugin().resolve("Команды.txt/inside"));

        files().refresh();

        assertTrue(Files.isRegularFile(plugin().resolve("HELP.txt")));
        assertTrue(Files.isRegularFile(plugin().resolve("SETTINGS.txt")));
        assertTrue(Files.isRegularFile(plugin().resolve("COMMANDS.txt")));
        assertTrue(read("HELP.txt").contains(PnLibraryIdentity.SUPPORT_URL));
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    void aFailureNeverThrowsAndIsReportedOnce() throws IOException {
        Files.createDirectories(directory.resolve("plugins"));
        Files.write(plugin(), "not a folder".getBytes(StandardCharsets.UTF_8));
        LibraryInfoFiles files = files();

        files.refresh();
        files.refresh();

        assertEquals(1, warnings.size(), warnings.toString());
    }

    @Test
    void switchesAreWrittenInRussianInTheOverview() {
        String text = files().settingsText();

        assertTrue(text.contains("Сейчас: выключено  (по умолчанию)"), text);
        assertTrue(text.contains("По умолчанию: выключено"), text);
        assertTrue(text.contains("/pn settings set privacy включено"), text);
        assertFalse(Pattern.compile("(Сейчас|По умолчанию): (true|false)").matcher(text).find(), text);
    }
}
