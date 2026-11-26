package org.pnfolder.pnlibrary.core.settings;

import org.pnfolder.pnlibrary.core.guide.LibraryCommandGuide;
import org.pnfolder.pnlibrary.core.guide.GuideEntry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class LibraryCommandGuideTest {
    /** The permissions the Bukkit plugin declares; the working directory of a core test is the module folder. */
    private final Set<String> declared = declaredPermissions();

    private static Set<String> declaredPermissions() {
        Set<String> declared = new LinkedHashSet<>();
        Pattern node = Pattern.compile("^  (pnlibrary\\.[a-z.]+):\\s*$");
        try {
            for (String line : Files.readAllLines(Paths.get("../../platforms/bukkit/runtime/src/main/resources/plugin.yml"))) {
                Matcher matcher = node.matcher(line);
                if (matcher.find()) {
                    declared.add(matcher.group(1));
                }
            }
        } catch (IOException error) {
            throw new IllegalStateException(error);
        }
        return declared;
    }

    @Test
    void everyPermissionOfPluginYmlIsInTheGuide() {
        assertTrue(declared.size() >= 10, "plugin.yml permissions were not found");
        Set<String> missing = new LinkedHashSet<>(declared);
        missing.removeAll(LibraryCommandGuide.getPermissions());
        assertTrue(missing.isEmpty(), "Команды.txt does not list: " + missing);
    }

    @Test
    void everyPermissionOfTheGuideIsDeclaredInPluginYmlOrBelongsToTheProxyOnly() {
        Set<String> unknown = new LinkedHashSet<>(LibraryCommandGuide.getPermissions());
        unknown.removeAll(declared);
        unknown.removeAll(LibraryCommandGuide.getProxyOnly());
        assertTrue(unknown.isEmpty(), "not in plugin.yml and not marked proxy-only: " + unknown);
        for (String permission : LibraryCommandGuide.getProxyOnly()) {
            assertTrue(!declared.contains(permission), "a proxy-only permission is declared for Bukkit");
        }
    }

    @Test
    void everyEntryIsCompleteAndNoCommandIsListedTwice() {
        Set<String> usages = new HashSet<>();
        for (GuideEntry entry : LibraryCommandGuide.getEntries()) {
            assertTrue(!entry.getUsage().trim().isEmpty() && !entry.getDescription().trim().isEmpty(), entry.getUsage());
            assertTrue(entry.getPermission() == null || entry.getPermission().startsWith("pnlibrary."), entry.getUsage());
            usages.add(entry.getUsage());
        }
        assertEquals(LibraryCommandGuide.getEntries().size(), usages.size(), "a command is listed twice");
    }

    @Test
    void anEntryWithoutAPermissionSaysWhy() {
        for (GuideEntry entry : LibraryCommandGuide.getEntries()) {
            if (entry.getPermission() == null) {
                assertTrue(entry.getNote() != null, entry.getUsage());
            }
        }
    }

    @Test
    void theGuideNamesEveryCommandWordOfTheControlCommand() {
        Set<String> words = new HashSet<>();
        for (GuideEntry entry : LibraryCommandGuide.getEntries()) {
            String[] parts = entry.getUsage().split(" ");
            words.add(parts.length > 1 ? parts[1] : "");
        }
        List<String> expected = Arrays.asList(
            "status", "check", "gui", "updates", "update", "events", "bus", "tasks", "stats", "placeholders", "actions",
            "settings", "configs", "deps", "debug", "servers", "groups", "money", "support");
        for (String word : expected) {
            assertTrue(words.contains(word), word);
        }
    }
}
