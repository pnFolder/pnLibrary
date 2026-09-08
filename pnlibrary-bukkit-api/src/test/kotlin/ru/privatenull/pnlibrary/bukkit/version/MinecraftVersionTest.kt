package ru.privatenull.pnlibrary.bukkit.version

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MinecraftVersionTest {
    @Test fun `parses Bukkit Paper and modern versions`() {
        assertEquals(MinecraftVersion.V1_8_8, MinecraftVersion.parse("1.8.8-R0.1-SNAPSHOT"))
        assertEquals(MinecraftVersion.V1_21_11, MinecraftVersion.parse("git-Paper-120 (MC: 1.21.11)"))
        assertEquals(MinecraftVersion.V26_2, MinecraftVersion.parse("26.2-112-c9e894d"))
        assertEquals(MinecraftVersion.UNKNOWN, MinecraftVersion.parse("27.4"))
    }

    @Test fun `compares semantic coordinates`() {
        assertTrue(MinecraftVersion.V1_21_11.isAtLeast(MinecraftVersion.V1_20_5))
        assertTrue(MinecraftVersion.V26_2.isBetween(MinecraftVersion.V26_1, MinecraftVersion.V26_2))
        assertFalse(MinecraftVersion.UNKNOWN.isAtLeast(MinecraftVersion.V1_8))
    }

    @Test fun `supports closed and open ranges`() {
        val legacy = MinecraftVersion.V1_8_8..MinecraftVersion.V1_12_2
        assertTrue(MinecraftVersion.V1_10_2 in legacy)
        assertTrue(MinecraftVersion.V1_12_2 in legacy)
        assertFalse(MinecraftVersion.V1_13 in legacy)
        assertTrue(MinecraftVersion.V26_2 in MinecraftVersionRange.atLeast(MinecraftVersion.V1_20_5))
        assertFalse(MinecraftVersion.V1_20_5 in MinecraftVersionRange.newerThan(MinecraftVersion.V1_20_5))
    }

    @Test fun `intersects ranges without using enum ordinal`() {
        val first = MinecraftVersionRange.between(MinecraftVersion.V1_8_8, MinecraftVersion.V1_16_5)
        val second = MinecraftVersionRange.between(MinecraftVersion.V1_12_2, MinecraftVersion.V1_20_6)
        assertEquals(MinecraftVersionRange.between(MinecraftVersion.V1_12_2, MinecraftVersion.V1_16_5), first.intersection(second))
        assertFalse(first.overlaps(MinecraftVersionRange.atLeast(MinecraftVersion.V1_17)))
    }
}
