package org.pnfolder.pnlibrary.bukkit.placeholders

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderAdapter
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderRequest
import org.pnfolder.pnlibrary.api.plugin.PluginId
import java.util.UUID

class PlaceholderApiRequestsTest {
    private val owner = PluginId.of("war")
    private val player = UUID(1, 2)
    private val seen = mutableListOf<PlaceholderRequest>()

    private fun requests(primary: Boolean) = PlaceholderApiRequests { primary }.also { requests ->
        requests.handlers.add("score.{who}", PublishedHandler(owner) { seen += it; "score of ${it.parameters["who"]}" })
        requests.handlers.add("empty", PublishedHandler(owner) { seen += it; null })
    }

    @Test
    fun `the answer is the resolver's text with the brace parts and the player`() {
        assertEquals("score of Alex", requests(primary = false).answer(player, "score.Alex"))
        val request = seen.single()
        assertEquals(mapOf("who" to "Alex"), request.parameters)
        assertEquals(player, request.playerId)
        assertEquals(owner, request.owner)
    }

    @Test
    fun `an unknown name or an empty value gives null`() {
        val requests = requests(primary = false)
        assertNull(requests.answer(null, "missing"))
        assertNull(requests.answer(null, "empty"))
    }

    @Test
    fun `the server thread is told not to wait and other threads may wait a second`() {
        requests(primary = true).answer(player, "score.A")
        requests(primary = false).answer(player, "score.B")
        assertEquals(listOf(0L, 1000L), seen.map { it.values[PlaceholderAdapter.WAIT_MILLIS_KEY] })
    }

    @Test
    fun `a slow resolver that honours the wait value does not hold the server thread`() {
        val requests = PlaceholderApiRequests { true }
        requests.handlers.add("slow", PublishedHandler(owner) { request ->
            val wait = request.values[PlaceholderAdapter.WAIT_MILLIS_KEY] as Long
            if (wait > 0) Thread.sleep(wait)
            "late"
        })
        val started = System.nanoTime()
        assertEquals("late", requests.answer(null, "slow"))
        assertTrue((System.nanoTime() - started) / 1_000_000 < 500)
    }
}
