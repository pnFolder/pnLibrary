package ru.privatenull.pnlibrary.core.cooldowns

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.UUID

class CooldownServiceImplTest {
    @Test
    fun `all operations use the same trimmed case insensitive action key`() {
        val service = CooldownServiceImpl()
        val player = UUID.randomUUID()

        assertTrue(service.acquire(player, "  LOGIN  ", Duration.ofMinutes(1)).allowed)
        assertTrue(service.has(player, "login"))
        assertTrue(service.reset(player, " Login "))
        assertFalse(service.has(player, "LOGIN"))
    }

    @Test
    fun `cleanup remains idempotent after close while reads reject`() {
        val service = CooldownServiceImpl()
        val player = UUID.randomUUID()
        service.set(player, "example", Duration.ofMinutes(1))

        service.close()

        assertDoesNotThrow { service.reset(player, "example") }
        assertDoesNotThrow { service.clear() }
        assertThrows(IllegalStateException::class.java) { service.remaining(player, "example") }
    }
}
