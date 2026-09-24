package ru.privatenull.pnlibrary.compatibility.bukkit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CompatibilityVersionTest {
    @Test void acceptsVersionsBySemanticOrder() {
        assertTrue(CompatibilityVersion.accepts("2.10.0", "2.9.0"));
        assertTrue(CompatibilityVersion.accepts("v2.2.0", "2.2.0-beta.1"));
        assertFalse(CompatibilityVersion.accepts("2.2.0-beta.1", "2.2.0"));
    }

    @Test void rejectsMalformedVersions() {
        assertThrows(IllegalArgumentException.class, () -> CompatibilityVersion.normalize("latest"));
    }
}
