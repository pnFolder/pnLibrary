package ru.privatenull.pnlibrary.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SemanticVersionTest {

    @Test
    void acceptsPrefixAndShortVersion() {
        assertEquals(SemanticVersion.parse("v1.2"), SemanticVersion.parse("1.2.0"));
    }

    @Test
    void stableReleaseIsNewerThanPrerelease() {
        assertTrue(SemanticVersion.parse("1.0.0").compareTo(
                SemanticVersion.parse("1.0.0-SNAPSHOT")) > 0);
    }

    @Test
    void comparesNumericPrereleasePartsAsNumbers() {
        assertTrue(SemanticVersion.parse("1.0.0-beta.10").compareTo(
                SemanticVersion.parse("1.0.0-beta.2")) > 0);
    }

    @Test
    void ignoresBuildMetadata() {
        assertEquals(
                SemanticVersion.parse("1.2.3+build.1"),
                SemanticVersion.parse("1.2.3+build.2")
        );
    }
}
