package ru.privatenull.pnlibrary.bootstrap.bukkit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BootstrapVersionTest {
    @Test void comparesSemanticCoreWithoutLexicographicMistakes() {
        assertTrue(BootstrapVersion.isAtLeast("2.10.0", "2.9.9"));
        assertTrue(BootstrapVersion.isAtLeast("v2.2.0", "2.2.0-beta.2"));
        assertFalse(BootstrapVersion.isAtLeast("2.2.0-beta.2", "2.2.0"));
        assertFalse(BootstrapVersion.isAtLeast("1.9.9", "2.0.0"));
    }

    @Test void rejectsNonSemanticVersions() {
        assertThrows(IllegalArgumentException.class, () -> BootstrapVersion.normalize("latest"));
    }
}
