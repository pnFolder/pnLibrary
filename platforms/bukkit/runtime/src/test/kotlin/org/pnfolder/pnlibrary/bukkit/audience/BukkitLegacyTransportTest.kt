package org.pnfolder.pnlibrary.bukkit.audience

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.title.Title
import org.bukkit.Server
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.pnfolder.pnlibrary.bukkit.PnLoggerBuffer
import java.util.UUID
import java.util.logging.Logger

/** A 1.8 server has no native Adventure and may lack adventure-platform-bukkit: everything must still reach the player as plain legacy text. */
class BukkitLegacyTransportTest {
    private val server = mock(Server::class.java)
    private val player = mock(Player::class.java).also { `when`(it.uniqueId).thenReturn(UUID.randomUUID()) }
    private val plugin = mock(Plugin::class.java).also {
        `when`(it.logger).thenReturn(Logger.getLogger("test"))
        `when`(it.server).thenReturn(server)
    }
    private val transport = BukkitAdventureTransport(plugin, PnLoggerBuffer(Logger.getAnonymousLogger()) { null }, null)

    @Test
    fun `a message becomes section-coded text without its click event`() {
        val message = Component.text("Open", NamedTextColor.GREEN).clickEvent(ClickEvent.runCommand("/pn"))

        transport.sendMessage(player, message)

        verify(player).sendMessage("§aOpen")
    }

    @Test
    fun `a hex color is reduced to the nearest of the sixteen legacy colors`() {
        transport.sendMessage(player, Component.text("x", TextColor.color(0xFF0000)))

        val sent = org.mockito.ArgumentCaptor.forClass(String::class.java)
        verify(player).sendMessage(sent.capture())
        assertTrue(sent.value.matches(Regex("§[0-9a-f]x")), sent.value)
    }

    @Test
    fun `a title goes through the two-line legacy call`() {
        transport.showTitle(player, Title.title(Component.text("Hi", NamedTextColor.RED), Component.text("there")))

        verify(player).sendTitle("§cHi", "there")
    }

    @Test
    fun `without Adventure the transport reports itself as legacy only`() {
        assertEquals(false, transport.nativeTransportAvailable)
    }
}
