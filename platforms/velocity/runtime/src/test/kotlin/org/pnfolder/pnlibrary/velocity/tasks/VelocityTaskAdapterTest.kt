package org.pnfolder.pnlibrary.velocity.tasks

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.tasks.spec.TaskExecution
import org.pnfolder.pnlibrary.spi.tasks.*
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

/** Checks task scheduling, entity-task warnings and shutdown on the Velocity scheduler. */
class VelocityTaskAdapterTest {
    private val WARN: (String) -> Unit = {}

    @Test
    fun `task created during shutdown is cancelled`() {
        val cancelled = AtomicBoolean()
        lateinit var adapter: VelocityTaskAdapter
        adapter = VelocityTaskAdapter(WARN) {
            adapter.close()
            PlatformTaskHandle { cancelled.compareAndSet(false, true) }
        }
        adapter.buildTask().from(PlatformTaskRequest(TaskExecution.Kind.GLOBAL, null, Duration.ZERO, null, Runnable {})).schedule()
        assertTrue(cancelled.get())
    }

    @Test
    fun `close attempts every cancellation even if one fails`() {
        var scheduled = 0
        var cancellations = 0
        val adapter = VelocityTaskAdapter(WARN) {
            val index = scheduled++
            PlatformTaskHandle {
                cancellations++
                if (index == 0) error("cancel failed")
                true
            }
        }
        repeat(2) {
            adapter.buildTask().from(PlatformTaskRequest(TaskExecution.Kind.GLOBAL, null, Duration.ZERO, null, Runnable {})).schedule()
        }
        assertThrows(IllegalStateException::class.java) { adapter.close() }
        assertEquals(2, cancellations)
        adapter.close()
        assertEquals(2, cancellations)
    }

    @Test
    fun `entity scheduling runs on the proxy scheduler and warns on every call`() {
        val warnings = mutableListOf<String>()
        var scheduled = 0
        val adapter = VelocityTaskAdapter({ warnings += it }) {
            scheduled++
            PlatformTaskHandle { true }
        }
        val request = PlatformTaskRequest(TaskExecution.Kind.ENTITY, Any(), Duration.ZERO, null, Runnable {})

        adapter.buildTask().from(request).schedule()
        adapter.buildTask().from(request).schedule()

        assertEquals(2, scheduled)
        assertEquals(2, warnings.size)
        assertTrue(warnings.all { it.contains("Entity-bound") })
    }

    @Test
    fun `adapter forwards native duration request and closes registrations`() {
        var seen: PlatformTaskRequest? = null
        val cancelled = AtomicBoolean()
        val adapter = VelocityTaskAdapter(WARN) { request ->
            seen = request
            PlatformTaskHandle { cancelled.compareAndSet(false, true) }
        }
        val request = PlatformTaskRequest(TaskExecution.Kind.ASYNC, null, Duration.ofMillis(5), null, Runnable {})
        adapter.buildTask().from(request).schedule()
        adapter.close()
        assertEquals(request.executionKind, seen?.executionKind)
        assertEquals(request.delay, seen?.delay)
        assertEquals(request.interval, seen?.interval)
        assertTrue(cancelled.get())
        assertThrows(IllegalStateException::class.java) { adapter.buildTask().from(request).schedule() }
    }

    @Test
    fun `completed one shot is released before adapter close`() {
        lateinit var callback: Runnable
        val cancelled = AtomicBoolean()
        val adapter = VelocityTaskAdapter(WARN) { request ->
            callback = request.callback
            PlatformTaskHandle { cancelled.compareAndSet(false, true) }
        }
        adapter.buildTask().from(PlatformTaskRequest(TaskExecution.Kind.GLOBAL, null, Duration.ZERO, null, Runnable {})).schedule()
        callback.run()
        adapter.close()
        assertFalse(cancelled.get())
    }
}
