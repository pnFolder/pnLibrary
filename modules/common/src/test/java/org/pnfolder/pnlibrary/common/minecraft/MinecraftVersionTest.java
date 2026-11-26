package org.pnfolder.pnlibrary.common.minecraft;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftVersionTest {
    @Test
    void parsesBukkitPaperAndModernVersions() {
        assertEquals(MinecraftVersion.V1_8_8, MinecraftVersion.parse("1.8.8-R0.1-SNAPSHOT"));
        assertEquals(MinecraftVersion.V1_21_11, MinecraftVersion.parse("git-Paper-120 (MC: 1.21.11)"));
        assertEquals(MinecraftVersion.V26_2, MinecraftVersion.parse("26.2-112-c9e894d"));
        assertEquals(MinecraftVersion.UNKNOWN, MinecraftVersion.parse("27.4"));
    }

    @Test
    void comparesSemanticCoordinates() {
        assertTrue(MinecraftVersion.V1_21_11.isAtLeast(MinecraftVersion.V1_20_5));
        assertTrue(MinecraftVersion.V26_2.isBetween(MinecraftVersion.V26_1, MinecraftVersion.V26_2));
        assertFalse(MinecraftVersion.UNKNOWN.isAtLeast(MinecraftVersion.V1_8));
    }

    @Test
    void supportsClosedAndOpenRanges() {
        MinecraftVersionRange legacy = MinecraftVersionRange.between(MinecraftVersion.V1_8_8, MinecraftVersion.V1_12_2);
        assertTrue(legacy.contains(MinecraftVersion.V1_10_2));
        assertTrue(legacy.contains(MinecraftVersion.V1_12_2));
        assertFalse(legacy.contains(MinecraftVersion.V1_13));
        assertTrue(MinecraftVersionRange.atLeast(MinecraftVersion.V1_20_5).contains(MinecraftVersion.V26_2));
        assertFalse(MinecraftVersionRange.newerThan(MinecraftVersion.V1_20_5).contains(MinecraftVersion.V1_20_5));
    }

    @Test
    void intersectsRangesWithoutUsingEnumOrdinal() {
        MinecraftVersionRange first = MinecraftVersionRange.between(MinecraftVersion.V1_8_8, MinecraftVersion.V1_16_5);
        MinecraftVersionRange second = MinecraftVersionRange.between(MinecraftVersion.V1_12_2, MinecraftVersion.V1_20_6);
        assertEquals(
            MinecraftVersionRange.between(MinecraftVersion.V1_12_2, MinecraftVersion.V1_16_5),
            first.intersection(second));
        assertFalse(first.overlaps(MinecraftVersionRange.atLeast(MinecraftVersion.V1_17)));
    }

    @Test
    void retainsUnknownNumericVersions() {
        MinecraftVersionInfo info = MinecraftVersion.parseInfo("27.4-99-custom");
        assertEquals(MinecraftVersion.UNKNOWN, info.getParsed());
        assertEquals("27.4-99-custom", info.getRaw());
        assertEquals(Integer.valueOf(27), info.getMajor());
        assertEquals(Integer.valueOf(4), info.getMinor());
        assertEquals(Integer.valueOf(0), info.getPatch());
        assertTrue(info.isNumeric());
    }

    @Test
    void listsSupportedVersionsNewestFirstWithoutUnknown() {
        List<MinecraftVersion> supported = MinecraftVersion.supported();
        assertEquals(MinecraftVersion.V26_2, supported.get(0));
        assertFalse(supported.contains(MinecraftVersion.UNKNOWN));
        assertThrows(UnsupportedOperationException.class, () -> supported.add(MinecraftVersion.V1_8));
    }
}
