package org.pnfolder.pnlibrary.core.events

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.pnfolder.pnlibrary.api.audiences.AudienceService
import org.pnfolder.pnlibrary.api.audiences.LibraryPlayer
import org.pnfolder.pnlibrary.api.events.player.PlayerJoinedEvent
import org.pnfolder.pnlibrary.api.events.player.PlayerLeftEvent
import org.pnfolder.pnlibrary.api.events.player.PlayerLifecycleEvent
import org.pnfolder.pnlibrary.api.plugin.PluginId
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import java.util.UUID
import java.util.function.Consumer

class PlayerLifecyclePublisherTest {
    private val id = UUID.randomUUID()

    private fun library(events: EventServiceImpl, player: LibraryPlayer?): PnLibrary {
        val audiences = mock(AudienceService::class.java)
        `when`(audiences.player(id)).thenReturn(player)
        val library = mock(PnLibrary::class.java)
        `when`(library.events).thenReturn(events)
        `when`(library.audiences).thenReturn(audiences)
        return library
    }

    @Test
    fun `join and quit reach portable listeners with the resolved player`() {
        val player = mock(LibraryPlayer::class.java)
        EventServiceImpl { _, _, _ -> }.use { events ->
            val seen = mutableListOf<PlayerLifecycleEvent>()
            val scope = events.scope(PluginId.of("test"))
            scope.subscribe(PlayerLifecycleEvent::class.java, Consumer { seen += it })
            val library = library(events, player)

            PlayerLifecyclePublisher.joined(library, id, "Steve")
            PlayerLifecyclePublisher.left(library, id, "Steve")

            assertEquals(listOf(PlayerJoinedEvent::class.java, PlayerLeftEvent::class.java), seen.map { it.javaClass })
            assertEquals(listOf("Steve", "Steve"), seen.map { it.name })
            assertEquals(listOf(id, id), seen.map { it.uniqueId })
            assertSame(player, seen.first().player)
        }
    }

    @Test
    fun `a player the platform no longer knows is announced without a player object`() {
        EventServiceImpl { _, _, _ -> }.use { events ->
            var seen: PlayerLeftEvent? = null
            events.scope(PluginId.of("test")).subscribe(PlayerLeftEvent::class.java, Consumer { seen = it })
            PlayerLifecyclePublisher.left(library(events, null), id, "Alex")
            assertEquals("Alex", seen?.name)
            assertNull(seen?.player)
        }
    }

    @Test
    fun `a closed event service drops the announcement instead of failing the platform listener`() {
        val events = EventServiceImpl { _, _, _ -> }
        val library = library(events, null)
        events.close()
        PlayerLifecyclePublisher.joined(library, id, "Steve")
    }
}
