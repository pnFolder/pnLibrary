package org.pnfolder.pnlibrary.velocity.tasks

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.tasks.TaskExecution
import org.pnfolder.pnlibrary.spi.tasks.*
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

class VelocityTaskAdapterTest {
    @Test
    fun `task created during shutdown is cancelled and rejected`() {
        val cancelled = AtomicBoolean()
        lateinit var adapter: VelocityTaskAdapter
        adapter = VelocityTaskAdapter {
            adapter.close()
            PlatformTaskHandle { cancelled.compareAndSet(false, true) }
        }
        assertThrows(IllegalStateException::class.java) {
            adapter.schedule(PlatformTaskRequest(TaskExecution.Kind.GLOBAL, null, Duration.ZERO, null, Runnable {}))
        }
        assertTrue(cancelled.get())
    }

    @Test
    fun `close attempts every cancellation even if one fails`() {
        var scheduled = 0
        var cancellations = 0
        val adapter = VelocityTaskAdapter {
            val index = scheduled++
            PlatformTaskHandle {
                cancellations++
                if (index == 0) error("cancel failed")
                true
            }
        }
        repeat(2) {
            adapter.schedule(PlatformTaskRequest(TaskExecution.Kind.GLOBAL, null, Duration.ZERO, null, Runnable {}))
        }
        assertThrows(IllegalStateException::class.java) { adapter.close() }
        assertEquals(2, cancellations)
        adapter.close()
        assertEquals(2, cancellations)
    }

    @Test
    fun `entity scheduling fails instead of silently running globally`() {
        val adapter = VelocityTaskAdapter { fail("Native scheduler must not be called") }
        assertThrows(IllegalArgumentException::class.java) {
            adapter.schedule(PlatformTaskRequest(TaskExecution.Kind.ENTITY, Any(), Duration.ZERO, null, Runnable {}))
        }
    }

    @Test
    fun `adapter forwards native duration request and closes registrations`() {
        var seen: PlatformTaskRequest? = null
        val cancelled = AtomicBoolean()
        val adapter = VelocityTaskAdapter { request ->
            seen = request
            PlatformTaskHandle { cancelled.compareAndSet(false, true) }
        }
        val request = PlatformTaskRequest(TaskExecution.Kind.ASYNC, null, Duration.ofMillis(5), null, Runnable {})
        adapter.schedule(request)
        adapter.close()
        assertEquals(request.executionKind, seen?.executionKind)
        assertEquals(request.delay, seen?.delay)
        assertEquals(request.interval, seen?.interval)
        assertTrue(cancelled.get())
        assertThrows(IllegalStateException::class.java) { adapter.schedule(request) }
    }

    @Test
    fun `completed one shot is released before adapter close`() {
        lateinit var callback: Runnable
        val cancelled = AtomicBoolean()
        val adapter = VelocityTaskAdapter { request ->
            callback = request.callback
            PlatformTaskHandle { cancelled.compareAndSet(false, true) }
        }
        adapter.schedule(PlatformTaskRequest(TaskExecution.Kind.GLOBAL, null, Duration.ZERO, null, Runnable {}))
        callback.run()
        adapter.close()
        assertFalse(cancelled.get())
    }
}
