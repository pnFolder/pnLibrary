package org.pnfolder.pnlibrary.bootstrap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BootstrapVersionTest {
    @Test
    void comparesPrereleaseIdentifiersInSemanticOrder() {
        String[] ordered = { "1.0.0-alpha", "1.0.0-alpha.1", "1.0.0-alpha.beta",
                "1.0.0-beta", "1.0.0-beta.2", "1.0.0-beta.10", "1.0.0-rc.1", "1.0.0" };
        for (int i = 1; i < ordered.length; i++) {
            assertTrue(BootstrapVersion.isAtLeast(ordered[i], ordered[i - 1]));
            assertFalse(BootstrapVersion.isAtLeast(ordered[i - 1], ordered[i]));
        }
    }

    @Test
    void ignoresBuildMetadataAndSupportsLargeNumbers() {
        assertTrue(BootstrapVersion.isAtLeast("1.0.0+build-1", "1.0.0"));
        assertTrue(BootstrapVersion.isAtLeast("1.0.0", "1.0.0+build-2"));
        assertFalse(BootstrapVersion.isAtLeast("1.0.0-beta.999999999999999999999",
                "1.0.0-beta.1000000000000000000000"));
        assertTrue(BootstrapVersion.isAtLeast("999999999999999999999.0.0", "2.0.0"));
    }

    @Test
    void comparesSemanticCoreWithoutLexicographicMistakes() {
        assertTrue(BootstrapVersion.isAtLeast("2.10.0", "2.9.9"));
        assertTrue(BootstrapVersion.isAtLeast("v2.2.0", "2.2.0-beta.2"));
        assertFalse(BootstrapVersion.isAtLeast("2.2.0-beta.2", "2.2.0"));
        assertFalse(BootstrapVersion.isAtLeast("1.9.9", "2.0.0"));
    }

    @Test
    void rejectsNonSemanticVersions() {
        assertThrows(IllegalArgumentException.class, () -> BootstrapVersion.normalize("latest"));
        for (String invalid : new String[] { "01.0.0", "1.0.0-beta.01", "1.0.0-beta..1", "1.0.0+" }) {
            assertThrows(IllegalArgumentException.class, () -> BootstrapVersion.normalize(invalid));
        }
    }
}
