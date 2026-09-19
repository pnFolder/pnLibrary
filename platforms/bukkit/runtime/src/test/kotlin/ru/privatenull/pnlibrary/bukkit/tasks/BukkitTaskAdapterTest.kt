package ru.privatenull.pnlibrary.bukkit.tasks

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration
import ru.privatenull.pnlibrary.api.tasks.TaskExecution
import ru.privatenull.pnlibrary.spi.tasks.PlatformTaskRequest

class BukkitTaskAdapterTest {
    @Test fun `duration conversion rounds partial ticks up`() {
        assertEquals(0, durationToTicks(Duration.ZERO))
        assertEquals(1, durationToTicks(Duration.ofMillis(1)))
        assertEquals(20, durationToTicks(Duration.ofSeconds(1)))
    }

    @Test fun `one shot wrapper releases after callback`() {
        var released = false
        val request = PlatformTaskRequest(TaskExecution.Kind.GLOBAL, null, Duration.ZERO, null, Runnable {})
        withCompletionRelease(request) { released = true }.callback.run()
        assertEquals(true, released)
    }
}
