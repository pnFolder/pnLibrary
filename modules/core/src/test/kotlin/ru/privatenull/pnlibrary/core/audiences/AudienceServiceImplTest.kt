package ru.privatenull.pnlibrary.core.audiences

import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.audiences.AudienceSender
import ru.privatenull.pnlibrary.api.actions.LibraryPlayer
import ru.privatenull.pnlibrary.api.actions.PlayerEffect
import ru.privatenull.pnlibrary.api.actions.PlayerParticle
import ru.privatenull.pnlibrary.spi.audiences.PlatformAudienceAdapter
import java.util.UUID

class AudienceServiceImplTest {
    @Test
    fun `lookups delegate while close suppresses stale resolution`() {
        val console = Sender("console", console = true)
        val player = Player(UUID.randomUUID(), "Alice")
        val adapter = RecordingAdapter(console, mutableListOf(player))
        val service = AudienceServiceImpl(adapter)

        assertSame(console, service.console())
        assertSame(player, service.player(player.uniqueId))
        assertSame(player, service.sender("native"))
        service.close()
        assertNull(service.player(player.uniqueId))
        assertNull(service.sender("native"))
        assertEquals(emptyList<LibraryPlayer>(), service.onlinePlayers())
    }

    @Test
    fun `dynamic and composite audiences deduplicate isolate failures and aggregate sound`() {
        val calls = mutableListOf<String>()
        val first = Sender("first", calls = calls)
        val broken = Sender("broken", calls = calls, failMessages = true)
        val last = Sender("last", calls = calls, soundAccepted = false)
        val members = mutableListOf<LibraryPlayer>()
        val service = AudienceServiceImpl(RecordingAdapter(Sender("console"), members))
        val composite = service.combine(listOf(first, broken, first, last))

        composite.sendMessage(Component.text("hello"))
        val accepted = composite.playSound(Sound.sound(
            org.junit.jupiter.api.assertDoesNotThrow { net.kyori.adventure.key.Key.key("test", "sound") },
            Sound.Source.MASTER, 1f, 1f,
        ))

        assertEquals(listOf("first:message", "broken:message", "last:message", "first:sound", "broken:sound", "last:sound"), calls)
        assertFalse(accepted)

        members += Player(UUID.randomUUID(), "NowOnline", calls)
        service.all().actionBar(Component.text("bar"))
        assertEquals("NowOnline:bar", calls.last())
    }

    private open class Sender(
        override val name: String,
        private val console: Boolean = false,
        private val calls: MutableList<String> = mutableListOf(),
        private val failMessages: Boolean = false,
        private val soundAccepted: Boolean = true,
    ) : AudienceSender {
        override val id = name
        override val isConsole get() = console
        override fun hasPermission(permission: String) = true
        override fun sendMessage(text: Component) { calls += "$name:message"; if (failMessages) error("delivery") }
        override fun actionBar(text: Component) { calls += "$name:bar" }
        override fun playSound(sound: Sound): Boolean { calls += "$name:sound"; return soundAccepted }
    }

    private class Player(
        override val uniqueId: UUID,
        name: String,
        calls: MutableList<String> = mutableListOf(),
    ) : Sender(name, calls = calls), LibraryPlayer {
        override val id get() = uniqueId.toString()
        override val isConsole get() = false
        override fun applyEffect(effect: PlayerEffect) = false
        override fun spawnParticle(particle: PlayerParticle) = false
    }

    private class RecordingAdapter(
        private val console: AudienceSender,
        private val players: MutableList<LibraryPlayer>,
    ) : PlatformAudienceAdapter {
        override fun console() = console
        override fun player(uniqueId: UUID) = players.firstOrNull { it.uniqueId == uniqueId }
        override fun sender(native: Any) = players.firstOrNull()
        override fun onlinePlayers() = players.toList()
    }
}
