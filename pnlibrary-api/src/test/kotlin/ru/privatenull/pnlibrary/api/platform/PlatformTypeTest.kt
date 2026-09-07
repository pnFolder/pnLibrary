package ru.privatenull.pnlibrary.api.platform

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlatformTypeTest {
    @Test
    fun `only API families are platform types`() {
        assertEquals(
            listOf(PlatformType.BUKKIT, PlatformType.BUNGEECORD, PlatformType.VELOCITY),
            PlatformType.entries,
        )
    }

    @Test
    fun `platform types describe server and proxy roles`() {
        assertTrue(PlatformType.BUKKIT.isServer)
        assertFalse(PlatformType.BUKKIT.isProxy)

        listOf(PlatformType.BUNGEECORD, PlatformType.VELOCITY).forEach {
            assertTrue(it.isProxy)
            assertFalse(it.isServer)
        }
    }

    @Test
    fun `variants belong to their compatible API family`() {
        val bukkitVariants = listOf(
            PlatformVariant.BUKKIT,
            PlatformVariant.PAPER,
            PlatformVariant.PURPUR,
            PlatformVariant.LEAF,
            PlatformVariant.FOLIA,
        )
        bukkitVariants.forEach { assertEquals(PlatformType.BUKKIT, it.type) }

        assertEquals(PlatformType.BUNGEECORD, PlatformVariant.BUNGEECORD.type)
        assertEquals(PlatformType.BUNGEECORD, PlatformVariant.WATERFALL.type)
        assertEquals(PlatformType.BUNGEECORD, PlatformVariant.NULLCORDX.type)
        assertEquals(PlatformType.VELOCITY, PlatformVariant.VELOCITY.type)
    }

    @Test
    fun `distribution belongs to the platform family`() {
        assertEquals("bukkit", PlatformType.BUKKIT.distributionArtifact)
        assertEquals("bungee", PlatformType.BUNGEECORD.distributionArtifact)
        assertEquals("velocity", PlatformType.VELOCITY.distributionArtifact)
    }
}
