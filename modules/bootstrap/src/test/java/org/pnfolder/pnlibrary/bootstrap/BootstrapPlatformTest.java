package org.pnfolder.pnlibrary.bootstrap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BootstrapPlatformTest {
    @Test
    void onlyTheUniversalJarIsDownloaded() {
        assertTrue(BootstrapPlatform.isUniversal("pnLibrary-1.2.0-universal.jar"));
        assertTrue(BootstrapPlatform.isUniversal("pnLibrary-1.2.0-beta.1-universal.jar"));
        assertFalse(BootstrapPlatform.isUniversal("pnLibrary-1.2.0-bukkit-java8.jar"));
        assertFalse(BootstrapPlatform.isUniversal("pnLibrary-1.2.0-velocity-java17.jar"));
        assertFalse(BootstrapPlatform.isUniversal("pnLibrary-api-1.2.0.jar"));
        assertFalse(BootstrapPlatform.isUniversal(null));
    }

    @Test
    void eachPlatformReadsItsOwnDescriptorOfTheSameJar() {
        assertEquals("pnLibrary", LibraryInstaller.declaredName(BootstrapPlatform.BUKKIT, "name: 'pnLibrary'\nversion: '1.2.0'\n"));
        assertEquals("pnLibrary", LibraryInstaller.declaredName(BootstrapPlatform.BUNGEECORD, "name: pnLibrary\r\nversion: 1\r\n"));
        assertEquals("pnlibrary", LibraryInstaller.declaredName(BootstrapPlatform.VELOCITY, "{\"id\": \"pnlibrary\", \"name\": \"pnLibrary\"}"));
        assertNull(LibraryInstaller.declaredName(BootstrapPlatform.BUKKIT, "version: 1\n"));
    }

    @Test
    void theRestartCountdownIsOffByDefaultAndAnnouncesEveryTenSecondsThenTheLastFive() {
        assertEquals(0, BootstrapOptions.builder("1.0.0").build().restartCountdown());
        assertEquals(30, BootstrapOptions.builder("1.0.0").restartCountdown(30).build().restartCountdown());
        assertThrows(IllegalArgumentException.class, () -> BootstrapOptions.builder("1.0.0").restartCountdown(301).build());
        assertThrows(IllegalArgumentException.class, () -> BootstrapOptions.builder("1.0.0").restartCountdown(-1).build());

        StringBuilder shown = new StringBuilder();
        for (int left = 30; left >= 1; left--) if (LibraryInstaller.announces(left)) shown.append(left).append(' ');
        assertEquals("30 20 10 5 4 3 2 1 ", shown.toString());
    }
}
