package org.pnfolder.pnlibrary.bungee

import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.md_5.bungee.api.CommandSender
import net.md_5.bungee.api.ProxyServer
import net.md_5.bungee.api.chat.BaseComponent
import net.md_5.bungee.api.connection.ProxiedPlayer
import net.md_5.bungee.api.plugin.Plugin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.pnfolder.pnlibrary.api.audiences.Delivery
import org.pnfolder.pnlibrary.api.audiences.PlayerCapability
import java.util.UUID

/** Checks how proxy senders and players are converted to portable audiences. */
class BungeeAudienceAdapterTest {
    private val proxy = mock(ProxyServer::class.java)
    private val plugin = mock(Plugin::class.java).also { `when`(it.proxy).thenReturn(proxy) }
    private val sent = mutableListOf<String>()

    // Varargs are not matched by argument matchers, so the console records what it is sent by method name.
    private val console = mock(CommandSender::class.java) { invocation ->
        when (invocation.method.name) {
            "getName" -> "CONSOLE"
            "sendMessage" -> {
                invocation.arguments.flatMap { if (it is Array<*>) it.toList() else listOf(it) }
                    .filterIsInstance<BaseComponent>().forEach { sent += it.toLegacyText() }
                null
            }
            else -> null
        }
    }
    private val adapter: BungeeAudienceAdapter

    init {
        `when`(proxy.console).thenReturn(console)
        adapter = BungeeAudienceAdapter(plugin)
    }

    private fun player(name: String): ProxiedPlayer {
        val player = mock(ProxiedPlayer::class.java)
        `when`(player.name).thenReturn(name)
        `when`(player.uniqueId).thenReturn(UUID.nameUUIDFromBytes(name.toByteArray()))
        return player
    }

    @Test
    fun `the console is a console audience`() {
        val sender = adapter.console()

        assertTrue(sender.isConsole)
        assertEquals("CONSOLE", sender.name)
    }

    @Test
    fun `other senders are wrapped and unknown objects are not`() {
        val other = mock(CommandSender::class.java).also { `when`(it.name).thenReturn("rcon") }

        assertFalse(adapter.sender(other)!!.isConsole)
        assertNotNull(adapter.sender(player("Alex")))
        assertNull(adapter.sender("not a sender"))
    }

    @Test
    fun `online players are found by id and listed`() {
        val alex = player("Alex")
        `when`(proxy.getPlayer(alex.uniqueId)).thenReturn(alex)
        `when`(proxy.players).thenReturn(listOf(alex))

        assertEquals("Alex", adapter.player(alex.uniqueId)?.name)
        assertNull(adapter.player(UUID.randomUUID()))
        assertEquals(listOf("Alex"), adapter.onlinePlayers().map { it.name })
    }

    @Test
    fun `a console action bar falls back to chat and sounds are unsupported`() {
        val sender = adapter.console()
        sender.actionBar(Component.text("hello"))

        assertEquals(listOf("hello"), sent.map(net.md_5.bungee.api.ChatColor::stripColor))
        assertEquals(Delivery.Unsupported, sender.playSound(mock(Sound::class.java)))
    }

    @Test
    fun `a player can read messages and the action bar but nothing a backend must render`() {
        val library = BungeeLibraryPlayer.of(player("Alex"))
        assertEquals(setOf(PlayerCapability.MESSAGE, PlayerCapability.ACTION_BAR), library.capabilities)
        assertEquals(Delivery.Unsupported, library.playSound(mock(Sound::class.java)))
    }
}
