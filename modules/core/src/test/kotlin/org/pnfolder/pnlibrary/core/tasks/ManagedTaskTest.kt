package org.pnfolder.pnlibrary.core.tasks

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.tasks.TaskConflictPolicy
import org.pnfolder.pnlibrary.api.tasks.TaskExecution
import org.pnfolder.pnlibrary.api.tasks.TaskSpec
import org.pnfolder.pnlibrary.api.tasks.TaskStatus
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskAdapter
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskHandle
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskRequest
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class ManagedTaskTest {
    private val adapter = FakeAdapter()
    private val terminal = mutableListOf<ManagedTask>()

    private fun task(spec: TaskSpec) = ManagedTask(Any(), Any(), spec, adapter, { _, _, _ -> }, { terminal += it })

    @Test
    fun `cancel wins once and later runs do nothing`() {
        val runs = AtomicInteger()
        val task = task(TaskSpec.builder().action { runs.incrementAndGet() }.build())
        assertTrue(task.cancelIfActive())
        assertFalse(task.cancelIfActive())
        task.invoke()
        assertEquals(0, runs.get())
        assertEquals(TaskStatus.CANCELLED, task.status)
        assertTrue(task.isCancelled)
        assertEquals(1, terminal.size)
    }

    @Test
    fun `cancel during the run is not overwritten when the run ends`() {
        val holder = arrayOfNulls<ManagedTask>(1)
        val task = task(TaskSpec.builder().interval(Duration.ofSeconds(1)).action { holder[0]!!.cancel() }.build())
        holder[0] = task
        task.invoke()
        assertEquals(TaskStatus.CANCELLED, task.status)
        assertEquals(1, terminal.size)
    }

    @Test
    fun `one shot task completes once and repeating task returns to scheduled`() {
        val once = task(TaskSpec.builder().action { }.build())
        once.invoke()
        once.invoke()
        assertEquals(TaskStatus.COMPLETED, once.status)
        assertEquals(1, once.snapshot().runCount)
        val repeat = task(TaskSpec.builder().interval(Duration.ofSeconds(1)).action { }.build())
        repeat.invoke()
        repeat.invoke()
        assertEquals(TaskStatus.SCHEDULED, repeat.status)
        assertEquals(2, repeat.snapshot().runCount)
        assertNotNull(repeat.snapshot().nextRunAt)
    }

    @Test
    fun `failing action marks the task failed once and reports it`() {
        val task = task(TaskSpec.builder().action { error("boom") }.build())
        task.invoke()
        assertEquals(TaskStatus.FAILED, task.status)
        assertTrue(task.snapshot().lastFailure!!.contains("boom"))
        assertEquals(1, terminal.size)
    }

    @Test
    fun `cancelWhen condition cancels the task before it runs`() {
        val runs = AtomicInteger()
        val flag = AtomicBoolean()
        val task = task(TaskSpec.builder().interval(Duration.ofSeconds(1)).cancelWhen { flag.get() }.action { runs.incrementAndGet() }.build())
        task.invoke()
        assertEquals(1, runs.get())
        flag.set(true)
        task.invoke()
        assertEquals(1, runs.get())
        assertEquals(TaskStatus.CANCELLED, task.status)
    }

    @Test
    fun `conflict by key keeps existing or replaces it`() {
        val service = ScheduledTasks(adapter)
        val scope = service.scope(Any())
        val first = scope.schedule(TaskSpec.builder().key("k").action { }.build())
        val kept = scope.schedule(TaskSpec.builder().key("k").conflictPolicy(TaskConflictPolicy.KEEP_EXISTING).action { }.build())
        assertSame(first, kept)
        val replaced = scope.schedule(TaskSpec.builder().key("k").conflictPolicy(TaskConflictPolicy.REPLACE).action { }.build())
        assertEquals(TaskStatus.CANCELLED, first.status)
        assertEquals(TaskStatus.SCHEDULED, replaced.status)
        service.close()
    }

    @Test
    fun `global task with zero delay is requested with zero delay and runs`() {
        val service = ScheduledTasks(adapter)
        val ran = AtomicBoolean()
        val handle = service.scope(Any()).schedule(TaskSpec.builder().execution(TaskExecution.global()).action { ran.set(true) }.build())
        val request = adapter.requests.single()
        assertEquals(TaskExecution.Kind.GLOBAL, request.executionKind)
        assertEquals(Duration.ZERO, request.delay)
        request.callback.run()
        assertTrue(ran.get())
        assertEquals(TaskStatus.COMPLETED, handle.status)
        service.close()
    }

    @Test
    fun `runNow starts a waiting task with zero delay and refuses a finished one`() {
        val runs = AtomicInteger()
        val task = task(TaskSpec.builder().delay(Duration.ofMinutes(5)).action { runs.incrementAndGet() }.build())
        assertTrue(task.runNow())
        assertEquals(Duration.ZERO, adapter.requests.single().delay)
        adapter.requests.single().callback.run()
        assertEquals(1, runs.get())
        assertEquals(TaskStatus.COMPLETED, task.status)
        assertFalse(task.runNow())
    }

    private class FakeAdapter : PlatformTaskAdapter {
        val requests = mutableListOf<PlatformTaskRequest>()

        override fun schedule(request: PlatformTaskRequest): PlatformTaskHandle {
            requests += request
            return object : PlatformTaskHandle {
                override fun cancel() = true
            }
        }

        override fun close() = Unit
    }
}
