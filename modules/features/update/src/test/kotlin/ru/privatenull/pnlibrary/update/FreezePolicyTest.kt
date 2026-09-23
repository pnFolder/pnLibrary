package ru.privatenull.pnlibrary.update

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.privatenull.pnlibrary.api.updates.ProductId
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class FreezePolicyTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `accepts only durations from one minute through thirty days`() {
        assertEquals(Duration.ofMinutes(1), FreezeDuration.parse("1m"))
        assertEquals(Duration.ofHours(2), FreezeDuration.parse("2h"))
        assertEquals(Duration.ofDays(30), FreezeDuration.parse("30d"))
        listOf("0m", "31d", "forever", "permanent", "1s").forEach { value ->
            assertThrows(IllegalArgumentException::class.java, { FreezeDuration.parse(value) }, value)
        }
    }

    @Test
    fun `persists absolute expiry and does not restart duration`() {
        val clock = MutableClock(Instant.parse("2026-09-17T10:00:00Z"))
        val file = directory.resolve("freezes.json")
        val market = ProductId.of("pnmarket")
        FreezeStore(file, clock).freeze(market, Duration.ofHours(2))

        clock.advance(Duration.ofMinutes(30))
        val restarted = FreezeStore(file, clock)
        assertEquals(Duration.ofMinutes(90), restarted.remaining(market))
    }

    @Test
    fun `expiry removes only the elapsed component`() {
        val clock = MutableClock(Instant.parse("2026-09-17T10:00:00Z"))
        val store = FreezeStore(directory.resolve("freezes.json"), clock)
        val market = ProductId.of("pnmarket")
        val auth = ProductId.of("pnauth")
        store.freeze(market, Duration.ofMinutes(1))
        store.freeze(auth, Duration.ofHours(1))

        clock.advance(Duration.ofMinutes(2))
        assertFalse(store.isFrozen(market))
        assertTrue(store.isFrozen(auth))
        assertEquals(setOf(auth), store.active().keys)
    }

    private class MutableClock(private var current: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneId.of("UTC")
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = current
        fun advance(duration: Duration) { current = current.plus(duration) }
    }
}
