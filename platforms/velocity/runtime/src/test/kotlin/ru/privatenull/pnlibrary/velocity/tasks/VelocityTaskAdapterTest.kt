package ru.privatenull.pnlibrary.velocity.tasks

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.tasks.TaskExecution
import ru.privatenull.pnlibrary.spi.tasks.*
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

class VelocityTaskAdapterTest {
    @Test fun `adapter forwards native duration request and closes registrations`() {
        var seen: PlatformTaskRequest? = null; val cancelled = AtomicBoolean()
        val adapter = VelocityTaskAdapter { request -> seen = request; PlatformTaskHandle { cancelled.compareAndSet(false, true) } }
        val request = PlatformTaskRequest(TaskExecution.Kind.ASYNC, null, Duration.ofMillis(5), null, Runnable {})
        adapter.schedule(request); adapter.close()
        assertEquals(request.executionKind, seen?.executionKind)
        assertEquals(request.delay, seen?.delay)
        assertEquals(request.interval, seen?.interval)
        assertTrue(cancelled.get())
        assertThrows(IllegalStateException::class.java) { adapter.schedule(request) }
    }

    @Test fun `completed one shot is released before adapter close`() {
        lateinit var callback: Runnable; val cancelled = AtomicBoolean()
        val adapter = VelocityTaskAdapter { request ->
            callback = request.callback
            PlatformTaskHandle { cancelled.compareAndSet(false, true) }
        }
        adapter.schedule(PlatformTaskRequest(TaskExecution.Kind.GLOBAL, null, Duration.ZERO, null, Runnable {}))
        callback.run(); adapter.close()
        assertFalse(cancelled.get())
    }
}
