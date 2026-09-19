package ru.privatenull.pnlibrary.bungee.tasks

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.tasks.TaskExecution
import ru.privatenull.pnlibrary.spi.tasks.*
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

class BungeeTaskAdapterTest {
    @Test fun `adapter forwards request and cancels native handle once`() {
        var seen: PlatformTaskRequest? = null; val cancelled = AtomicBoolean()
        val adapter = BungeeTaskAdapter { request -> seen = request; PlatformTaskHandle { cancelled.compareAndSet(false, true) } }
        val request = PlatformTaskRequest(TaskExecution.Kind.ENTITY, "player", Duration.ofSeconds(2),
            Duration.ofSeconds(3), Runnable {})
        val handle = adapter.schedule(request)
        assertSame(request, seen); assertTrue(handle.cancel()); assertFalse(handle.cancel()); assertTrue(cancelled.get())
    }
}
