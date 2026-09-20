package ru.privatenull.pnlibrary.bukkit.updates

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID

class UpdateConfirmationTokensTest {
    @Test
    fun `token is player plan revision action expiry and single use bound`() {
        var now = Instant.EPOCH
        val tokens = UpdateConfirmationTokens(clock = { now })
        val player = UUID.randomUUID()
        val plan = UUID.randomUUID()
        val token = tokens.issue(player, plan, 7, UpdateAction.DOWNLOAD, Duration.ofSeconds(30))

        assertTrue(token.length >= 43)
        assertFalse(tokens.consume(token, UUID.randomUUID(), plan, 7, UpdateAction.DOWNLOAD))
        assertFalse(tokens.consume(token, player, plan, 8, UpdateAction.DOWNLOAD))
        assertFalse(tokens.consume(token, player, plan, 7, UpdateAction.RESTART))
        assertTrue(tokens.consume(token, player, plan, 7, UpdateAction.DOWNLOAD))
        assertFalse(tokens.consume(token, player, plan, 7, UpdateAction.DOWNLOAD))

        val expired = tokens.issue(player, plan, 7, UpdateAction.RESTART, Duration.ofSeconds(1))
        now = now.plusSeconds(2)
        assertFalse(tokens.consume(expired, player, plan, 7, UpdateAction.RESTART))
        assertEquals(0, tokens.cleanup())
    }
}
