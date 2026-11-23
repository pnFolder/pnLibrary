package org.pnfolder.pnlibrary.api.player

import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.player.Delivery
import org.pnfolder.pnlibrary.api.player.LibraryPlayer
import org.pnfolder.pnlibrary.api.player.PlayerCapability
import org.pnfolder.pnlibrary.api.player.PlayerEffect
import org.pnfolder.pnlibrary.api.player.PlayerParticle
import org.pnfolder.pnlibrary.api.sender.id
import org.pnfolder.pnlibrary.api.sender.isConsole
import org.pnfolder.pnlibrary.api.sender.isPlayer
import java.util.UUID

class PlayerContractsTest {
    @Test
    fun `library player supplies sender identity defaults`() {
        val id = UUID.randomUUID()
        val player = TestPlayer(id)

        assertEquals(id.toString(), player.id)
        assertTrue(player.isPlayer)
        assertFalse(player.isConsole)
        assertTrue(player.hasPermission("example.use"))
    }

    @Test
    fun `default player capabilities describe proxy-safe operations`() {
        val player = TestPlayer(UUID.randomUUID())

        assertEquals(
            setOf(PlayerCapability.MESSAGE, PlayerCapability.ACTION_BAR),
            player.capabilities,
        )
    }

    private class TestPlayer(override val uniqueId: UUID) : LibraryPlayer {
        override val name = "Alice"
        override fun hasPermission(permission: String) = permission == "example.use"
        override fun sendMessage(text: Component) = Unit
        override fun sendActionBar(text: Component) = Unit
        override fun playSound(sound: Sound) = Unit
        override fun applyEffect(effect: PlayerEffect) = Delivery.Unsupported
        override fun spawnParticle(particle: PlayerParticle) = Delivery.Unsupported
    }
}
