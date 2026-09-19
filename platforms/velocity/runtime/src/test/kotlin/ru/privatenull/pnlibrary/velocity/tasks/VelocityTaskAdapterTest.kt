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
        assertSame(request, seen); assertTrue(cancelled.get())
        assertThrows(IllegalStateException::class.java) { adapter.schedule(request) }
    }
}
