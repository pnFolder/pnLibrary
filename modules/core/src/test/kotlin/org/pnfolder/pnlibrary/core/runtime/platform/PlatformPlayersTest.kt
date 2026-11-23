package org.pnfolder.pnlibrary.core.runtime.platform

import net.kyori.adventure.audience.Audience
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.player.Delivery
import org.pnfolder.pnlibrary.api.player.LibraryPlayer
import org.pnfolder.pnlibrary.api.player.PlayerEffect
import org.pnfolder.pnlibrary.api.player.PlayerParticle
import org.pnfolder.pnlibrary.spi.player.PlatformPlayerAdapter
import java.util.UUID

class PlatformPlayersTest {
    @Test
    fun `lookups delegate while close suppresses stale resolution`() {
        val console = Sender("console")
        val player = Player(UUID.randomUUID(), "Alice")
        val adapter = RecordingAdapter(console, mutableListOf(player))
        val service = PlatformPlayers(adapter)

        assertSame(console, service.console())
        assertSame(player, service.player(player.uniqueId))
        assertSame(player, service.sender("native"))
        service.close()
        assertNull(service.player(player.uniqueId))
        assertNull(service.sender("native"))
        assertEquals(emptyList<LibraryPlayer>(), service.onlinePlayers())
    }

    @Test
    fun `dynamic and composite audiences deduplicate isolate failures and play sound for each`() {
        val calls = mutableListOf<String>()
        val first = Sender("first", calls = calls)
        val broken = Sender("broken", calls = calls, failMessages = true)
        val last = Sender("last", calls = calls)
        val members = mutableListOf<LibraryPlayer>()
        val service = PlatformPlayers(RecordingAdapter(Sender("console"), members))
        val composite = service.combine(listOf(first, broken, first, last))

        composite.sendMessage(Component.text("hello"))
        composite.playSound(Sound.sound(
            org.junit.jupiter.api.assertDoesNotThrow { Key.key("test", "sound") },
            Sound.Source.MASTER, 1f, 1f,
        ))

        assertEquals(listOf("first:message", "broken:message", "last:message", "first:sound", "broken:sound", "last:sound"), calls)

        members += Player(UUID.randomUUID(), "NowOnline", calls)
        service.all().sendActionBar(Component.text("bar"))
        assertEquals("NowOnline:bar", calls.last())
    }

    @Test
    fun `a receiver that throws is reported once and the others still get the message`() {
        val calls = mutableListOf<String>()
        val failures = mutableListOf<Throwable>()
        val service = PlatformPlayers(RecordingAdapter(Sender("console"), mutableListOf())) { failures += it }
        val composite = service.combine(listOf(Sender("broken", calls = calls, failMessages = true), Sender("last", calls = calls)))

        repeat(2) { composite.sendMessage(Component.text("hello")) }

        assertEquals(1, failures.size)
        assertEquals(listOf("broken:message", "last:message", "broken:message", "last:message"), calls)
    }

    private open class Sender(
        val name: String,
        private val calls: MutableList<String> = mutableListOf(),
        private val failMessages: Boolean = false,
    ) : Audience {
        fun hasPermission(permission: String) = true
        override fun pointers() = org.pnfolder.pnlibrary.api.sender.senderPointers(name, (this as? LibraryPlayer)?.uniqueId) { true }
        override fun sendMessage(text: Component) {
            calls += "$name:message"
            if (failMessages) error("delivery")
        }

        override fun sendActionBar(text: Component) { calls += "$name:bar" }

        override fun playSound(sound: Sound) {
            calls += "$name:sound"
        }
    }

    private class Player(
        override val uniqueId: UUID,
        name: String,
        calls: MutableList<String> = mutableListOf(),
    ) : Sender(name, calls = calls), LibraryPlayer {
        override fun pointers() = super<Sender>.pointers()
        override fun applyEffect(effect: PlayerEffect) = Delivery.Unsupported
        override fun spawnParticle(particle: PlayerParticle) = Delivery.Unsupported
    }

    private class RecordingAdapter(
        private val console: Audience,
        private val players: MutableList<LibraryPlayer>,
    ) : PlatformPlayerAdapter {
        override fun console() = console
        override fun player(uniqueId: UUID) = players.firstOrNull { it.uniqueId == uniqueId }
        override fun sender(native: Any) = players.firstOrNull()
        override fun onlinePlayers() = players.toList()
    }
}
