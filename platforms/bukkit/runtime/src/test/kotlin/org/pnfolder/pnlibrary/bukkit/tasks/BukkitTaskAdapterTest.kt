package org.pnfolder.pnlibrary.bukkit.tasks

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.tasks.TaskExecution
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskHandle
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskRequest
import org.pnfolder.pnlibrary.spi.tasks.TrackingPlatformTaskAdapter
import java.time.Duration

class BukkitTaskAdapterTest {
    @Test
    fun `duration conversion rounds partial ticks up`() {
        assertEquals(0, durationToTicks(Duration.ZERO))
        assertEquals(1, durationToTicks(Duration.ofMillis(1)))
        assertEquals(20, durationToTicks(Duration.ofSeconds(1)))
    }

    @Test
    fun `one shot task is released after callback`() {
        val adapter = RecordingTaskAdapter()
        val request = PlatformTaskRequest(TaskExecution.Kind.GLOBAL, null, Duration.ZERO, null, Runnable {})
        val handle = adapter.schedule(request)
        assertEquals(1, adapter.active)
        adapter.callback!!.run()
        assertEquals(true, handle.cancel())
        assertEquals(0, adapter.active)
    }

    private class RecordingTaskAdapter : TrackingPlatformTaskAdapter() {
        var callback: Runnable? = null
        var active = 0

        override fun scheduleNative(request: PlatformTaskRequest): PlatformTaskHandle {
            callback = request.callback
            active++
            return PlatformTaskHandle { active--; true }
        }
    }
}
