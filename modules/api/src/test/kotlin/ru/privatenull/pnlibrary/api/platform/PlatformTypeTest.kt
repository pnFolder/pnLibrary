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
    fun `type identifiers are stable and independent of implementations`() {
        assertEquals("bukkit", PlatformType.BUKKIT.id)
        assertEquals("bungeecord", PlatformType.BUNGEECORD.id)
        assertEquals("velocity", PlatformType.VELOCITY.id)
    }

}
