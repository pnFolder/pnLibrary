package org.pnfolder.pnlibrary.api.compat.declaration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.compat.MinecraftSupport;
import org.pnfolder.pnlibrary.common.minecraft.MinecraftVersion;

class MinecraftVersionsTest {
    private static MinecraftSupport check(MinecraftVersions versions, String server) {
        return versions.check(server == null ? null : MinecraftVersion.parseInfo(server));
    }

    @Test
    @DisplayName("nothing declared accepts every version")
    void nothingDeclaredAcceptsEveryVersion() {
        assertEquals(MinecraftSupport.NOT_DECLARED, check(MinecraftVersions.NONE, "1.21.11"));
        assertTrue(MinecraftVersions.NONE.accepts("1.21.11"));
        assertNull(MinecraftVersions.NONE.getDescription());
    }

    @Test
    @DisplayName("a range includes both ends and tells older from newer")
    void aRangeIncludesBothEndsAndTellsOlderFromNewer() {
        MinecraftVersions versions = MinecraftVersions.between("1.16.5", "1.21.4");

        assertEquals(MinecraftSupport.SUPPORTED, check(versions, "1.16.5"));
        assertEquals(MinecraftSupport.SUPPORTED, check(versions, "1.18.2"));
        assertEquals(MinecraftSupport.SUPPORTED, check(versions, "1.21.4"));
        assertEquals(MinecraftSupport.OLDER_THAN_DECLARED, check(versions, "1.16.4"));
        assertEquals(MinecraftSupport.NEWER_THAN_DECLARED, check(versions, "1.21.5"));
    }

    @Test
    @DisplayName("from accepts a release the library has never heard of")
    void fromAcceptsAReleaseTheLibraryHasNeverHeardOf() {
        MinecraftVersions versions = MinecraftVersions.from("1.16.5");

        assertEquals(MinecraftSupport.SUPPORTED, check(versions, "1.99.3"));
        assertEquals(MinecraftSupport.SUPPORTED, check(versions, "2.0"));
        assertEquals(MinecraftSupport.OLDER_THAN_DECLARED, check(versions, "1.8.8"));
    }

    @Test
    @DisplayName("only the listed versions pass and the gaps between them are excluded")
    void onlyTheListedVersionsPassAndTheGapsBetweenThemAreExcluded() {
        MinecraftVersions versions = MinecraftVersions.only("1.20.1", "1.20.4");

        assertEquals(MinecraftSupport.SUPPORTED, check(versions, "1.20.4"));
        assertEquals(MinecraftSupport.EXCLUDED, check(versions, "1.20.2"));
        assertEquals(MinecraftSupport.NEWER_THAN_DECLARED, check(versions, "1.21"));
    }

    @Test
    @DisplayName("a version that cannot be read is unknown and the description lists what was declared")
    void aVersionThatCannotBeReadIsUnknownAndTheDescriptionListsWhatWasDeclared() {
        MinecraftVersions versions = MinecraftVersions.between("1.16.5", "1.21.4").plus(MinecraftVersions.only("1.8.8"));

        assertEquals(MinecraftSupport.UNKNOWN, check(versions, null));
        assertEquals(MinecraftSupport.UNKNOWN, check(versions, "Paper"));
        assertEquals("1.16.5-1.21.4, 1.8.8", versions.getDescription());
    }

    @Test
    @DisplayName("ready versions split the game at the 1_13 flattening")
    void readyVersionsSplitTheGameAtThe113Flattening() {
        assertTrue(MinecraftVersions.LEGACY.accepts(MinecraftVersion.V1_8_8));
        assertTrue(MinecraftVersions.LEGACY.accepts("1.12.2"));
        assertFalse(MinecraftVersions.LEGACY.accepts("1.13"));
        assertTrue(MinecraftVersions.MODERN.accepts("1.21.11"));
        assertFalse(MinecraftVersions.MODERN.accepts("1.12.2"));
        assertTrue(MinecraftVersions.ANY.accepts("3.0"));
        assertEquals("любая", MinecraftVersions.ANY.getDescription());
    }

    @Test
    @DisplayName("parts are joined with a plus and a gap between them is refused by accepts")
    void partsAreJoinedWithAPlusAndAGapBetweenThemIsRefusedByAccepts() {
        MinecraftVersions joined = MinecraftVersions.LEGACY.plus(MinecraftVersions.from("1.16.5"));

        assertTrue(joined.accepts("1.8.8"));
        assertFalse(joined.accepts("1.14.4"));
        assertTrue(joined.accepts("1.17"));
    }

    @Test
    @DisplayName("an unknown constant, an empty list, a backwards range and a text that is no version are refused")
    void anUnknownConstantAnEmptyListABackwardsRangeAndATextThatIsNoVersionAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> MinecraftVersions.from(MinecraftVersion.UNKNOWN));
        assertThrows(IllegalArgumentException.class, () -> MinecraftVersions.only(new String[0]));
        assertThrows(IllegalArgumentException.class, () -> MinecraftVersions.between("1.21", "1.16"));
        assertThrows(IllegalArgumentException.class, () -> MinecraftVersions.from("latest"));
    }
}
