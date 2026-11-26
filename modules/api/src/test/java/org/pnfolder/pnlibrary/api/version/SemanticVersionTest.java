package org.pnfolder.pnlibrary.api.version;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SemanticVersionTest {
    @Test
    @DisplayName("numeric prerelease comparison supports values beyond integer limits")
    void numericPrereleaseComparisonSupportsValuesBeyondIntegerLimits() {
        SemanticVersion lower = SemanticVersion.parse("2.0.0-beta.999999999999999999999");
        SemanticVersion higher = SemanticVersion.parse("2.0.0-beta.1000000000000000000000");
        assertTrue(lower.compareTo(higher) < 0);
        assertTrue(higher.compareTo(lower) > 0);
        assertTrue(higher.compareTo(SemanticVersion.parse("2.0.0-beta.alpha")) < 0);
        assertEquals(0, higher.compareTo(SemanticVersion.parse(higher.toString())));
    }

    @Test
    @DisplayName("prerelease identifiers cannot be mutated through the public value")
    void prereleaseIdentifiersCannotBeMutatedThroughThePublicValue() {
        SemanticVersion version = SemanticVersion.parse("2.0.0-beta.1");
        assertThrows(UnsupportedOperationException.class, () -> version.getPrerelease().set(0, "alpha"));
        assertEquals("2.0.0-beta.1", version.toString());
    }

    @Test
    @DisplayName("numeric prerelease identifiers compare numerically")
    void numericPrereleaseIdentifiersCompareNumerically() {
        assertTrue(SemanticVersion.parse("2.0.0-beta.10").compareTo(SemanticVersion.parse("2.0.0-beta.2")) > 0);
    }

    @Test
    @DisplayName("channel suffixes order from dev to stable and a bare release is newest")
    void channelSuffixesOrderFromDevToStableAndABareReleaseIsNewest() {
        List<SemanticVersion> order = new ArrayList<>();
        for (String suffix : Arrays.asList("dev.9", "alpha.1", "beta.1", "rc.1", "stable.1", "stable.2")) {
            order.add(SemanticVersion.parse("0.1.0-" + suffix));
        }
        List<SemanticVersion> shuffled = new ArrayList<>(order);
        Collections.reverse(shuffled);
        Collections.rotate(shuffled, 2);
        Collections.sort(shuffled);
        assertEquals(order, shuffled);
        SemanticVersion last = order.get(order.size() - 1);
        assertTrue(SemanticVersion.parse("0.1.0").compareTo(last) > 0);
        assertTrue(SemanticVersion.parse("0.1.1-dev.1").compareTo(last) > 0);
    }

    @Test
    @DisplayName("rejects invalid semver leading zeros and empty identifiers")
    void rejectsInvalidSemverLeadingZerosAndEmptyIdentifiers() {
        assertNull(SemanticVersion.tryParse("02.0.0"));
        assertNull(SemanticVersion.tryParse("2.0.0-beta..1"));
        assertNull(SemanticVersion.tryParse("2.0.0-beta.01"));
        assertNull(SemanticVersion.tryParse("2.0.0+!"));
    }

    @Test
    @DisplayName("compares alphanumeric prerelease identifiers case sensitively")
    void comparesAlphanumericPrereleaseIdentifiersCaseSensitively() {
        assertTrue(SemanticVersion.parse("1.0.0-BETA").compareTo(SemanticVersion.parse("1.0.0-beta")) < 0);
    }

    @Test
    @DisplayName("validates and ignores build metadata for precedence")
    void validatesAndIgnoresBuildMetadataForPrecedence() {
        assertEquals(SemanticVersion.parse("1.2.3+linux.x64"), SemanticVersion.parse("1.2.3+windows.x64"));
    }

    @Test
    @DisplayName("rejects inverted range")
    void rejectsInvertedRange() {
        assertThrows(IllegalArgumentException.class, () -> VersionRange.closed("3.0.0", "2.0.0"));
    }

    @Test
    @DisplayName("a range prints in interval notation and equal versions hash alike")
    void aRangePrintsInIntervalNotationAndEqualVersionsHashAlike() {
        assertEquals("[2.0.0, 3.0.0]", VersionRange.closed("2.0.0", "3.0.0").toString());
        assertEquals("(1.0.0, *]", VersionRange.greaterThan("1.0.0").toString());
        assertEquals("[*, 2.0.0)", VersionRange.until("2.0.0").toString());
        assertEquals(SemanticVersion.parse("1.2.3+a").hashCode(), SemanticVersion.parse("1.2.3+b").hashCode());
    }
}
