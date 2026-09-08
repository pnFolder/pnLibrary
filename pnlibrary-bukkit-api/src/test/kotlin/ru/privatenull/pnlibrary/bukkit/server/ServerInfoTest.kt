package ru.privatenull.pnlibrary.bukkit.server

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.bukkit.version.MinecraftVersion
import ru.privatenull.pnlibrary.bukkit.version.MinecraftVersionRange

class ServerInfoTest {
    private val server = ServerInfo(
        name = "Paper",
        version = "git-Paper-120",
        minecraftVersion = MinecraftVersion.V1_21_4,
        rawMinecraftVersion = "1.21.4",
    )

    @Test
    fun `server exposes software and minecraft versions separately`() {
        assertEquals("Paper git-Paper-120", server.displayName)
        assertEquals("git-Paper-120", server.version)
        assertEquals("1.21.4", server.rawMinecraftVersion)
    }

    @Test
    fun `server provides readable compatibility checks`() {
        assertTrue(server.isMinecraftAtLeast(MinecraftVersion.V1_20_5))
        assertTrue(server.supports(MinecraftVersionRange.atLeast(MinecraftVersion.V1_21)))
        assertFalse(server.supports(MinecraftVersionRange.atMost(MinecraftVersion.V1_20_6)))
    }
}
