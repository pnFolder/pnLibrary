package org.pnfolder.pnlibrary.bukkit.audience

import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import org.bukkit.Server
import org.bukkit.command.ConsoleCommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyFloat
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.pnfolder.pnlibrary.api.audience.Delivery
import org.pnfolder.pnlibrary.api.audience.PlayerEffect
import org.pnfolder.pnlibrary.api.audience.PlayerParticle
import org.pnfolder.pnlibrary.bukkit.PnLoggerBuffer
import java.time.Duration
import java.util.UUID
import java.util.logging.Logger

class BukkitLibraryPlayerTest {
    private val server = mock(Server::class.java)
    private val console = mock(ConsoleCommandSender::class.java)
    private val player = mock(Player::class.java)
    private val audiences = BukkitAdventureTransport(plugin(), PnLoggerBuffer(java.util.logging.Logger.getAnonymousLogger()) { null }, null)
    private val library = BukkitLibraryPlayer(player, audiences)
    private val bell = Sound.sound(Key.key("minecraft:block.note_block.bell"), Sound.Source.MASTER, 1f, 1f)

    init {
        `when`(player.uniqueId).thenReturn(UUID.randomUUID())
        `when`(player.server).thenReturn(server)
        `when`(server.consoleSender).thenReturn(console)
    }

    private fun plugin(): Plugin = mock(Plugin::class.java).also {
        `when`(it.logger).thenReturn(Logger.getLogger("test"))
        `when`(it.server).thenReturn(server)
    }

    @Test
    fun `a sound falls back to the native API and is delivered`() {
        assertEquals(Delivery.delivered(), audiences.playSound(player, bell))

        verify(player).playSound(any(), anyString(), anyFloat(), anyFloat())
    }

    @Test
    fun `a native sound failure keeps its cause`() {
        val cause = IllegalStateException("no sound")
        doThrow(cause).`when`(player).playSound(any(), anyString(), anyFloat(), anyFloat())

        val failed = audiences.playSound(player, bell)

        assertSame(cause, failed.failure)
    }

    @Test
    fun `a linkage error from an incompatible server is a failed delivery, not a crash`() {
        val cause = NoSuchMethodError("playSound")
        doThrow(cause).`when`(player).playSound(any(), anyString(), anyFloat(), anyFloat())

        assertSame(cause, audiences.playSound(player, bell).failure)
    }

    @Test
    fun `a failing JVM is not hidden`() {
        doThrow(OutOfMemoryError()).`when`(player).playSound(any(), anyString(), anyFloat(), anyFloat())

        assertThrows(OutOfMemoryError::class.java) { audiences.playSound(player, bell) }
    }

    @Test
    fun `a command runs as the player or as the console`() {
        `when`(server.dispatchCommand(player, "home")).thenReturn(true)
        `when`(server.dispatchCommand(console, "save-all")).thenReturn(false)

        assertEquals(Delivery.delivered(), library.runCommand("home", asConsole = false))
        assertEquals(Delivery.unsupported(), library.runCommand("save-all", asConsole = true))
    }

    @Test
    fun `an effect the server does not know is unsupported`() {
        val effect = PlayerEffect("no_such_effect", Duration.ofSeconds(5), 0, true, true, true)

        assertEquals(Delivery.unsupported(), library.applyEffect(effect))
    }

    @Test
    fun `particles are unsupported where the server has no particle API`() {
        assertEquals(Delivery.unsupported(), library.spawnParticle(PlayerParticle("flame", 1, 0.0, 0.0, 0.0, 0.0)))
    }
}
