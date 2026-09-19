package ru.privatenull.pnlibrary.bukkit.tasks

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration

class BukkitTaskAdapterTest {
    @Test fun `duration conversion rounds partial ticks up`() {
        assertEquals(0, durationToTicks(Duration.ZERO))
        assertEquals(1, durationToTicks(Duration.ofMillis(1)))
        assertEquals(20, durationToTicks(Duration.ofSeconds(1)))
    }
}
