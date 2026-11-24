package org.pnfolder.pnlibrary.api.audience

import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
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
    fun `a player offers only the features it implements`() {
        val plain = TestPlayer(UUID.randomUUID())
        val withInventory = InventoryPlayer(UUID.randomUUID())

        assertNull(plain.feature<PlayerInventory>())
        assertSame(withInventory, withInventory.feature<PlayerInventory>())
        assertNull(withInventory.feature<PlayerEffects>())
    }

    @Test
    fun `player pointers give name, nickname, language and permission`() {
        val id = UUID.randomUUID()
        val locale = java.util.Locale.GERMAN
        val who = object : net.kyori.adventure.audience.Audience {
            override fun pointers() = playerPointers({ "Alice" }, id, { Component.text("Al") }, { locale }) { it == "x" }
        }
        val anonymous = object : net.kyori.adventure.audience.Audience {
            override fun pointers() = playerPointers({ "Bob" }, id, { null }, { null }) { false }
        }

        assertEquals("Alice", who.name)
        assertEquals(Component.text("Al"), who.displayName)
        assertEquals(locale, who.locale)
        assertTrue(who.hasPermission("x"))
        assertEquals(Component.text("Bob"), anonymous.displayName)
        assertEquals(null, anonymous.locale)
    }

    private class InventoryPlayer(uniqueId: UUID) : TestPlayer(uniqueId), PlayerInventory {
        override fun countItems(query: ItemQuery) = 3
    }

    private open class TestPlayer(override val uniqueId: UUID) : LibraryPlayer {
        override fun pointers() = senderPointers("Alice", uniqueId) { it == "example.use" }
        override fun sendMessage(text: Component) = Unit
        override fun sendActionBar(text: Component) = Unit
        override fun playSound(sound: Sound) = Unit
    }
}
