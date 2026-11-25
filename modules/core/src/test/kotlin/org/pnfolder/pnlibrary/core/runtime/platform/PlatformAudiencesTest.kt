package org.pnfolder.pnlibrary.core.runtime.platform

import net.kyori.adventure.audience.Audience
import org.pnfolder.pnlibrary.api.audience.name
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.audience.OnlinePlayer
import org.pnfolder.pnlibrary.spi.audience.PlatformAudienceAdapter
import java.util.UUID

class PlatformAudiencesTest {
    @Test
    fun `lookups delegate while close suppresses stale resolution`() {
        val console = Sender("console")
        val player = Player(UUID.randomUUID(), "Alice")
        val adapter = RecordingAdapter(console, mutableListOf(player))
        val service = PlatformAudiences(adapter)

        assertSame(adapter.console(), service.console())
        assertSame(player, service.player(player.uniqueId))
        assertSame(player, service.of("native"))
        service.close()
        assertNull(service.player(player.uniqueId))
        assertNull(service.of("native"))
        assertEquals(emptyList<OnlinePlayer>(), service.players())
    }

    @Test
    fun `a player is found by name ignoring case`() {
        val player = Player(UUID.randomUUID(), "Alice")
        val service = PlatformAudiences(RecordingAdapter(Sender("console"), mutableListOf(player)))

        assertSame(player, service.player("aLiCe"))
        assertNull(service.player("Bob"))
    }

    @Test
    fun `dynamic and composite audiences deduplicate isolate failures and play sound for each`() {
        val calls = mutableListOf<String>()
        val first = Player(UUID.randomUUID(), "first", calls)
        val broken = Player(UUID.randomUUID(), "broken", calls, failMessages = true)
        val last = Player(UUID.randomUUID(), "last", calls)
        val members = mutableListOf<OnlinePlayer>(first, broken, first, last)
        val service = PlatformAudiences(RecordingAdapter(Sender("console"), members))
        val composite = service

        composite.sendMessage(Component.text("hello"))
        composite.playSound(Sound.sound(
            org.junit.jupiter.api.assertDoesNotThrow { Key.key("test", "sound") },
            Sound.Source.MASTER, 1f, 1f,
        ))

        assertEquals(listOf("first:message", "broken:message", "last:message", "first:sound", "broken:sound", "last:sound"), calls)

        members += Player(UUID.randomUUID(), "NowOnline", calls)
        service.sendActionBar(Component.text("bar"))
        assertEquals("NowOnline:bar", calls.last())
    }

    @Test
    fun `a receiver that throws is reported once and the others still get the message`() {
        val calls = mutableListOf<String>()
        val failures = mutableListOf<Throwable>()
        val members = mutableListOf<OnlinePlayer>(
            Player(UUID.randomUUID(), "broken", calls, failMessages = true),
            Player(UUID.randomUUID(), "last", calls),
        )
        val service = PlatformAudiences(RecordingAdapter(Sender("console"), members)) { failures += it }
        val composite = service

        repeat(2) { composite.sendMessage(Component.text("hello")) }

        assertEquals(1, failures.size)
        assertEquals(listOf("broken:message", "last:message", "broken:message", "last:message"), calls)
    }

    @Test
    fun `the audiences follow who is online and can be filtered`() {
        val calls = mutableListOf<String>()
        val members = mutableListOf<OnlinePlayer>(Player(UUID.randomUUID(), "Alice", calls), Player(UUID.randomUUID(), "Bob", calls))
        val service = PlatformAudiences(RecordingAdapter(Sender("console", calls = calls), members))

        service.filterAudience { it.name == "Bob" }.sendActionBar(Component.text("bar"))
        service.sendActionBar(Component.text("bar"))

        assertEquals(listOf("Bob:bar", "console:bar", "Alice:bar", "Bob:bar"), calls)
    }

    private open class Sender(
        val name: String,
        private val calls: MutableList<String> = mutableListOf(),
        private val failMessages: Boolean = false,
    ) : Audience {
        override fun pointers() = org.pnfolder.pnlibrary.api.audience.senderPointers(name, (this as? OnlinePlayer)?.uniqueId) { true }
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
        failMessages: Boolean = false,
    ) : Sender(name, calls = calls, failMessages = failMessages), OnlinePlayer {
        override fun pointers() = super<Sender>.pointers()
    }

    private class RecordingAdapter(
        console: Audience,
        private val players: MutableList<OnlinePlayer>,
    ) : PlatformAudienceAdapter {
        private val console = org.pnfolder.pnlibrary.spi.audience.PlatformConsole(console)

        override fun console() = console
        override fun player(uniqueId: UUID) = players.firstOrNull { it.uniqueId == uniqueId }
        override fun of(native: Any) = players.firstOrNull()
        override fun players() = players.toList()
    }
}
