package org.pnfolder.pnlibrary.core.plugin.module

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.UUID

class InMemoryCooldownsTest {
    @Test
    fun `all operations use the same trimmed case insensitive action key`() {
        val service = InMemoryCooldowns()
        val player = UUID.randomUUID()

        assertTrue(service.acquire(player, "  LOGIN  ", Duration.ofMinutes(1)).allowed)
        assertTrue(service.has(player, "login"))
        assertTrue(service.reset(player, " Login "))
        assertFalse(service.has(player, "LOGIN"))
    }

    @Test
    fun `cleanup remains idempotent after close while reads reject`() {
        val service = InMemoryCooldowns()
        val player = UUID.randomUUID()
        service.set(player, "example", Duration.ofMinutes(1))

        service.close()

        assertDoesNotThrow { service.reset(player, "example") }
        assertDoesNotThrow { service.clear() }
        assertThrows(IllegalStateException::class.java) { service.remaining(player, "example") }
    }

    @Test
    fun `active lists the cooldowns of one player and clear removes only that player's`() {
        val service = InMemoryCooldowns()
        val player = UUID.randomUUID()
        val other = UUID.randomUUID()
        service.set(player, "kit", Duration.ofMinutes(10))
        service.set(player, "home", Duration.ofMinutes(1))
        service.set(other, "kit", Duration.ofMinutes(1))

        assertEquals(listOf("kit", "home"), service.active(player).keys.toList())
        assertEquals(2, service.clear(player))
        assertTrue(service.active(player).isEmpty())
        assertTrue(service.has(other, "kit"))
    }

    @Test
    fun `expired entries of players nobody asks about are swept away`() {
        val service = InMemoryCooldowns()
        repeat(300) { service.set(UUID.randomUUID(), "once", Duration.ofNanos(1)) }
        Thread.sleep(5)
        repeat(300) { service.set(UUID.randomUUID(), "later", Duration.ofMinutes(1)) }

        assertTrue(service.size <= 300)
    }
}
