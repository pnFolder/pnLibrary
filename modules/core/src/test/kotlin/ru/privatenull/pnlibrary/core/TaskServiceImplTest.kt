package ru.privatenull.pnlibrary.core

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.tasks.*
import ru.privatenull.pnlibrary.core.tasks.TaskServiceImpl
import ru.privatenull.pnlibrary.spi.tasks.*
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

class TaskServiceImplTest {
    @Test fun `task can cancel itself and native handle is cancelled`() {
        val adapter = RecordingAdapter(); val service = TaskServiceImpl(adapter)
        val handle = service.scope(Any()).schedule(TaskSpec.builder()
            .name("self-stop").interval(Duration.ofSeconds(1)).action { it.cancel() }.build())
        adapter.fire(0)
        assertEquals(TaskStatus.CANCELLED, handle.status)
        assertTrue(adapter.handles[0].cancelled.get())
    }

    @Test fun `duplicate names are listed while keyed conflicts follow policy`() {
        val adapter = RecordingAdapter(); val scope = TaskServiceImpl(adapter).scope(Any())
        repeat(2) { scope.schedule(spec("same")) }
        assertEquals(2, scope.query(TaskQuery.builder().nameContains("same").build()).size)
        val first = scope.schedule(TaskSpec.builder().key("singleton").action { }.build())
        assertThrows(IllegalStateException::class.java) {
            scope.schedule(TaskSpec.builder().key("singleton").action { }.build())
        }
        val kept = scope.schedule(TaskSpec.builder().key("singleton")
            .conflictPolicy(TaskConflictPolicy.KEEP_EXISTING).action { }.build())
        assertSame(first, kept)
    }

    @Test fun `false conditions skip repeat but complete one shot`() {
        val adapter = RecordingAdapter(); val scope = TaskServiceImpl(adapter).scope(Any())
        val repeating = scope.schedule(TaskSpec.builder().interval(Duration.ofSeconds(1))
            .condition { false }.action { fail("must not run") }.build())
        val once = scope.schedule(TaskSpec.builder().condition { false }.action { fail("must not run") }.build())
        assertNotNull(repeating.snapshot().nextRunAt)
        adapter.fire(0); adapter.fire(1)
        assertEquals(TaskStatus.SCHEDULED, repeating.status)
        assertEquals(1, repeating.snapshot().skippedCount)
        assertEquals(TaskStatus.COMPLETED, once.status)
    }

    @Test fun `scope query cannot see another owner and history is bounded`() {
        val adapter = RecordingAdapter()
        val service = TaskServiceImpl(adapter, TaskServiceSettings(historyCapacity = 1))
        val a = service.scope(Any()); val b = service.scope(Any())
        a.schedule(spec("a")); b.schedule(spec("b")); adapter.fire(0); adapter.fire(1)
        assertEquals(0, a.query().size)
        assertEquals(1, service.query().size)
        assertEquals("b", service.query().single().name)
    }

    @Test fun `registration callback cancelling before attachment cancels returned native handle`() {
        val adapter = RecordingAdapter(fireDuringSchedule = true)
        val handle = TaskServiceImpl(adapter).scope(Any()).schedule(TaskSpec.builder().action { it.cancel() }.build())
        assertTrue(handle.isCancelled)
        assertTrue(adapter.handles.single().cancelled.get())
    }

    @Test fun `completed synchronous task cancels native handle after attachment`() {
        val adapter = RecordingAdapter(fireDuringSchedule = true)
        val handle = TaskServiceImpl(adapter).scope(Any()).schedule(TaskSpec.builder().action { }.build())
        assertEquals(TaskStatus.COMPLETED, handle.status)
        assertTrue(adapter.handles.single().cancelled.get())
    }

    private fun spec(name: String) = TaskSpec.builder().name(name).action { }.build()
    private class RecordingAdapter(private val fireDuringSchedule: Boolean = false) : PlatformTaskAdapter {
        val requests = mutableListOf<PlatformTaskRequest>(); val handles = mutableListOf<NativeHandle>()
        override fun schedule(request: PlatformTaskRequest): PlatformTaskHandle {
            requests += request; val handle = NativeHandle(); handles += handle
            if (fireDuringSchedule) request.callback.run()
            return handle
        }
        fun fire(index: Int) = requests[index].callback.run()
    }
    private class NativeHandle : PlatformTaskHandle {
        val cancelled = AtomicBoolean()
        override fun cancel(): Boolean = cancelled.compareAndSet(false, true)
    }
}
