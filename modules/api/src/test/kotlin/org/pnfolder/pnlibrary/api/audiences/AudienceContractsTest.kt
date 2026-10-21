package org.pnfolder.pnlibrary.api.audiences

import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class AudienceContractsTest {
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
        override fun actionBar(text: Component) = Unit
        override fun playSound(sound: Sound) = true
        override fun applyEffect(effect: PlayerEffect) = false
        override fun spawnParticle(particle: PlayerParticle) = false
    }
}
