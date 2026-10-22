package org.pnfolder.pnlibrary.bootstrap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.console.ConsoleTheme;

class InstalledCardTest {
    private static String shown(boolean stable, String version) {
        List<String> lines = new ArrayList<>();
        BootstrapConsole console = new BootstrapConsole() {
            @Override public ConsoleTheme theme(Tone tone) { return ConsoleTheme.plain(); }
            @Override public void send(String line) { lines.add(line); }
        };
        LibraryInstaller installer = new LibraryInstaller(
                BootstrapPlatform.BUKKIT, BootstrapOptions.builder().build(), "pnCases", console);
        Path file = Paths.get("pnLibrary-" + version + "-universal.jar");
        LibraryRelease release = new LibraryRelease(
                version, file.toString(), URI.create("https://example.com/x.jar"), "0000000000000000000000000000000000000000000000000000000000000000", 1024L, stable);
        installer.installed(new LibraryInstaller.Placement(release, file, null), false);
        return String.join("\n", lines);
    }

    @Test
    void aPreReleaseIsExplainedInTheConsole() {
        String card = shown(false, "2.0.0-beta.1");

        assertTrue(card.contains("предварительный (нестабильный)"), card);
        assertTrue(card.contains("Это предварительная версия"), card);
        assertTrue(card.contains("Стабильного релиза"), card);
        assertTrue(card.contains("2.0.0-beta.1"), card);
    }

    @Test
    void aStableReleaseGetsNoWarning() {
        String card = shown(true, "1.0.0");

        assertTrue(card.contains("стабильный"), card);
        assertFalse(card.contains("Это предварительная версия"), card);
    }
}
