package org.pnfolder.pnlibrary.core.tasks

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.plugin.PluginId
import org.pnfolder.pnlibrary.api.tasks.*
import org.pnfolder.pnlibrary.api.tasks.info.TaskId
import org.pnfolder.pnlibrary.api.tasks.info.TaskQuery
import org.pnfolder.pnlibrary.api.tasks.info.TaskSnapshot
import org.pnfolder.pnlibrary.api.tasks.info.TaskStatus
import org.pnfolder.pnlibrary.api.tasks.spec.TaskConflictPolicy
import org.pnfolder.pnlibrary.api.tasks.spec.TaskSpec
import org.pnfolder.pnlibrary.api.tasks.spec.schedule
import org.pnfolder.pnlibrary.api.tasks.spec.spawn
import org.pnfolder.pnlibrary.core.tasks.ScheduledTasks
import org.pnfolder.pnlibrary.core.tasks.TaskControl
import org.pnfolder.pnlibrary.spi.tasks.*
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

class ScheduledTasksTest {
    @Test
    fun `closed service rejects both native owner and named module scopes`() {
        val adapter = RecordingAdapter()
        val service = ScheduledTasks(adapter)
        service.close()
        assertThrows(IllegalStateException::class.java) { service.scope(Any()) }
        assertThrows(IllegalStateException::class.java) {
            service.scope(Any(), PluginId.of("module"))
        }
        assertTrue(adapter.requests.isEmpty())
    }

    @Test
    fun `scope cleanup continues after native cancellation fails`() {
        val adapter = RecordingAdapter(failingCancelIndex = 0)
        val service = ScheduledTasks(adapter)
        val scope = service.scope(Any())
        val first = scope.schedule(spec("first"))
        val second = scope.schedule(spec("second"))
        assertThrows(IllegalStateException::class.java) { scope.close() }
        assertTrue(adapter.handles.all { it.cancelled.get() })
        assertNull(service.get(first.id))
        assertNull(service.get(second.id))
        assertTrue(scope.query().all { it.status == TaskStatus.CANCELLED })
        assertThrows(IllegalStateException::class.java) { scope.schedule(spec("late")) }
        service.close()
        assertTrue(adapter.closed)
    }

    @Test
    fun `service cleanup closes all owners and adapter despite a cancellation failure`() {
        val adapter = RecordingAdapter(failingCancelIndex = 0)
        val service = ScheduledTasks(adapter)
        val first = service.scope(Any()).schedule(spec("first-owner"))
        val second = service.scope(Any()).schedule(spec("second-owner"))
        assertThrows(IllegalStateException::class.java) { service.close() }
        assertTrue(adapter.handles.all { it.cancelled.get() })
        assertTrue(adapter.closed)
        assertNull(service.get(first.id))
        assertNull(service.get(second.id))
    }

    @Test
    fun `failed action is archived even when cancellation and error logging also fail`() {
        val adapter = RecordingAdapter(failingCancelIndex = 0)
        val actionFailure = IllegalStateException("action failed")
        val loggerFailure = IllegalArgumentException("logger failed")
        val reported = mutableListOf<Throwable>()
        val service = ScheduledTasks(adapter, errorLogger = { _, _, error ->
            reported += error
            throw loggerFailure
        })
        val task = service.scope(Any()).schedule(TaskSpec.builder().action { throw actionFailure }.build())
        adapter.fire(0)
        assertEquals(TaskStatus.FAILED, task.status)
        assertNull(service.get(task.id))
        assertNotNull(service.query().single().lastCompletedAt)
        assertEquals(listOf(actionFailure), reported)
        assertEquals(listOf("native cancellation failed", "logger failed"), actionFailure.suppressed.map { it.message })
        service.close()
    }

    @Test
    fun `task can cancel itself and native handle is cancelled`() {
        val adapter = RecordingAdapter()
        val service = ScheduledTasks(adapter)
        val handle = service.scope(Any()).schedule(TaskSpec.builder()
            .name("self-stop").interval(Duration.ofSeconds(1)).action { it.cancel() }.build())
        adapter.fire(0)
        assertEquals(TaskStatus.CANCELLED, handle.status)
        assertTrue(adapter.handles[0].cancelled.get())
    }

    @Test
    fun `the context reports the run, the plan, the settings and cancels after a limit`() {
        val adapter = RecordingAdapter()
        val scope = ScheduledTasks(adapter).scope(Any())
        val seen = mutableListOf<String>()
        val handle = scope.schedule(TaskSpec.builder().name("ctx").key("k").tag("t").interval(Duration.ofSeconds(1))
            .action { c ->
                seen += "${c.runNumber}:${c.isFirstRun}:${c.every(2)}:${c.key}:${c.tags}:${c.isRepeating}:${c.previousStartedAt != null}"
                check(!c.lag.isNegative)
                check(c.snapshot().runCount == c.runNumber)
                c.cancelAfter(3)
            }.build())

        repeat(3) { adapter.fire(0) }

        assertEquals(listOf("1:true:false:k:[t]:true:false", "2:false:true:k:[t]:true:true", "3:false:false:k:[t]:true:true"), seen)
        assertEquals(TaskStatus.CANCELLED, handle.status)
    }

    @Test
    fun `a task starts a follow-up that stays in the same scope`() {
        val adapter = RecordingAdapter()
        val scope = ScheduledTasks(adapter).scope(Any())
        val ran = mutableListOf<String>()
        scope.schedule(TaskSpec.builder().name("parent").action { c ->
            c.later(Duration.ofSeconds(5)) { ran += "later" }
        }.build())

        adapter.fire(0)

        assertEquals(2, adapter.requests.size)
        assertEquals(Duration.ofSeconds(5), adapter.requests[1].delay)
        val child = scope.query(TaskQuery.builder().tag("subtask").build()).single()
        assertEquals("parent / later", child.name)
        adapter.fire(1)
        assertEquals(listOf("later"), ran)
    }

    @Test
    fun `each run of a repeating task is planned one interval after the previous start`() {
        val adapter = RecordingAdapter()
        val scope = ScheduledTasks(adapter).scope(Any())
        val planned = mutableListOf<java.time.Instant>()
        scope.schedule(TaskSpec.builder().interval(Duration.ofSeconds(10)).action { planned += it.scheduledAt }.build())

        repeat(2) { adapter.fire(0) }

        assertTrue(planned[1].isAfter(planned[0].plusSeconds(9)))
    }

    @Test
    fun `a run that starts later than max lag is skipped`() {
        val adapter = RecordingAdapter()
        val scope = ScheduledTasks(adapter).scope(Any())
        val ran = AtomicBoolean()
        val handle = scope.schedule(TaskSpec.builder().maxLag(Duration.ofMillis(1)).interval(Duration.ofSeconds(1))
            .action { ran.set(true) }.build())

        Thread.sleep(30)
        adapter.fire(0)

        assertFalse(ran.get())
        assertEquals(1, handle.snapshot().skippedCount)
        assertEquals(TaskStatus.SCHEDULED, handle.status)
    }

    @Test
    fun `a repeating task survives tolerated failures in a row and then fails`() {
        val adapter = RecordingAdapter()
        val scope = ScheduledTasks(adapter).scope(Any())
        val handle = scope.schedule(TaskSpec.builder().interval(Duration.ofSeconds(1)).tolerateFailures(2)
            .action { error("boom") }.build())

        adapter.fire(0)
        adapter.fire(0)
        assertEquals(TaskStatus.SCHEDULED, handle.status)
        adapter.fire(0)

        assertEquals(TaskStatus.FAILED, handle.status)
        assertEquals(3, handle.snapshot().failedCount)
    }

    @Test
    fun `a success resets the failure count`() {
        val adapter = RecordingAdapter()
        val scope = ScheduledTasks(adapter).scope(Any())
        var fail = true
        val handle = scope.schedule(TaskSpec.builder().interval(Duration.ofSeconds(1)).tolerateFailures(1)
            .action { if (fail) error("boom") }.build())

        adapter.fire(0)
        fail = false
        adapter.fire(0)
        fail = true
        adapter.fire(0)

        assertEquals(TaskStatus.SCHEDULED, handle.status)
    }

    @Test
    fun `a lag handler hears about a late run and can skip it`() {
        val adapter = RecordingAdapter()
        val scope = ScheduledTasks(adapter).scope(Any())
        val heard = mutableListOf<org.pnfolder.pnlibrary.api.tasks.info.TaskLag>()
        val ran = AtomicBoolean()
        val handle = scope.schedule(TaskSpec.builder().interval(Duration.ofMillis(10))
            .onLag(Duration.ofMillis(5)) { lag -> heard += lag; org.pnfolder.pnlibrary.api.tasks.run.LagResponse.SKIP }
            .action { ran.set(true) }.build())

        Thread.sleep(50)
        adapter.fire(0)

        assertFalse(ran.get())
        assertEquals(1, heard.size)
        assertTrue(heard[0].missedRuns >= 3)
        assertTrue(handle.snapshot().worstLag!! >= Duration.ofMillis(40))
        assertEquals(1, handle.snapshot().skippedCount)
    }

    @Test
    fun `a lag handler can cancel the task for good`() {
        val adapter = RecordingAdapter()
        val scope = ScheduledTasks(adapter).scope(Any())
        val ran = AtomicBoolean()
        val handle = scope.schedule(TaskSpec.builder().interval(Duration.ofMillis(10))
            .onLag(Duration.ofMillis(5)) { org.pnfolder.pnlibrary.api.tasks.run.LagResponse.CANCEL }
            .action { ran.set(true) }.build())

        Thread.sleep(50)
        adapter.fire(0)

        assertFalse(ran.get())
        assertEquals(TaskStatus.CANCELLED, handle.status)
        assertNull(handle.snapshot().nextRunAt)
    }

    @Test
    fun `a lag handler that throws does not stop the run`() {
        val adapter = RecordingAdapter()
        val scope = ScheduledTasks(adapter).scope(Any())
        val ran = AtomicBoolean()
        scope.schedule(TaskSpec.builder().interval(Duration.ofMillis(10))
            .onLag(Duration.ofMillis(1)) { error("handler broke") }
            .action { ran.set(true) }.build())

        Thread.sleep(20)
        adapter.fire(0)

        assertTrue(ran.get())
    }

    @Test
    fun `a subtask knows its parent and a linked one is cancelled with it`() {
        val adapter = RecordingAdapter()
        val scope = ScheduledTasks(adapter).scope(Any())
        val parent = scope.schedule(TaskSpec.builder().name("parent").interval(Duration.ofSeconds(1)).action { c ->
            if (c.runNumber == 1L) {
                c.repeat(3, Duration.ofSeconds(1)) { }
                c.later(Duration.ofSeconds(1)) { }
            }
        }.build())

        adapter.fire(0)

        val children = scope.query(TaskQuery.builder().tag("subtask").build())
        assertEquals(2, children.size)
        assertTrue(children.all { it.parentId == parent.id })
        parent.cancel()
        val states = scope.query(TaskQuery.builder().tag("subtask").build()).associate { it.name to it.status }
        assertEquals(TaskStatus.CANCELLED, states["parent / repeat"])
        assertEquals(TaskStatus.SCHEDULED, states["parent / later"])
    }

    @Test
    fun `duplicate names are listed while keyed conflicts follow policy`() {
        val adapter = RecordingAdapter()
        val scope = ScheduledTasks(adapter).scope(Any())
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

    @Test
    fun `false conditions skip repeat but complete one shot`() {
        val adapter = RecordingAdapter()
        val scope = ScheduledTasks(adapter).scope(Any())
        val repeating = scope.schedule(TaskSpec.builder().interval(Duration.ofSeconds(1))
            .condition { false }.action { fail("must not run") }.build())
        val once = scope.schedule(TaskSpec.builder().condition { false }.action { fail("must not run") }.build())
        assertNotNull(repeating.snapshot().nextRunAt)
        adapter.fire(0)
        adapter.fire(1)
        assertEquals(TaskStatus.SCHEDULED, repeating.status)
        assertEquals(1, repeating.snapshot().skippedCount)
        assertEquals(TaskStatus.COMPLETED, once.status)
    }

    @Test
    fun `scope query cannot see another owner and history is bounded`() {
        val adapter = RecordingAdapter()
        val service = ScheduledTasks(adapter, TaskServiceSettings(historyCapacity = 1))
        val a = service.scope(Any())
        val b = service.scope(Any())
        a.schedule(spec("a"))
        b.schedule(spec("b"))
        adapter.fire(0)
        adapter.fire(1)
        assertEquals(0, a.query().size)
        assertEquals(1, service.query().size)
        assertEquals("b", service.query().single().name)
    }

    @Test
    fun `registration callback cancelling before attachment cancels returned native handle`() {
        val adapter = RecordingAdapter(fireDuringSchedule = true)
        val handle = ScheduledTasks(adapter).scope(Any()).schedule(TaskSpec.builder().action { it.cancel() }.build())
        assertTrue(handle.isCancelled)
        assertTrue(adapter.handles.single().cancelled.get())
    }

    @Test
    fun `completed synchronous task cancels native handle after attachment`() {
        val adapter = RecordingAdapter(fireDuringSchedule = true)
        val handle = ScheduledTasks(adapter).scope(Any()).schedule(TaskSpec.builder().action { }.build())
        assertEquals(TaskStatus.COMPLETED, handle.status)
        assertTrue(adapter.handles.single().cancelled.get())
    }

    @Test
    fun `closed scope rejects new tasks`() {
        val scope = ScheduledTasks(RecordingAdapter()).scope(Any())
        scope.close()
        assertThrows(IllegalStateException::class.java) {
            scope.schedule(TaskSpec.builder().action { }.build())
        }
    }

    @Test
    fun `closing one owner cancels only that owner's tasks`() {
        val adapter = RecordingAdapter()
        val service = ScheduledTasks(adapter)
        val pluginOwner = Any()
        val otherOwner = Any()
        val pluginTask = service.scope(pluginOwner).schedule(spec("plugin"))
        val otherTask = service.scope(otherOwner).schedule(spec("other"))

        service.close(pluginOwner)

        assertEquals(TaskStatus.CANCELLED, pluginTask.status)
        assertTrue(pluginTask.isCancelled)
        assertEquals(TaskStatus.SCHEDULED, otherTask.status)
        assertFalse(otherTask.isCancelled)
        assertTrue(adapter.handles[0].cancelled.get())
        assertFalse(adapter.handles[1].cancelled.get())
    }

    @Test
    fun `canonical task lookup works for service and owner scope`() {
        val service = ScheduledTasks(RecordingAdapter())
        val scope = service.scope(Any())
        val handle = scope.schedule(TaskSpec.builder().key("cleanup").action { }.build())

        assertSame(handle, service.get(handle.id))
        assertSame(handle, service.require(handle.id))
        assertSame(handle, scope.get(handle.id))
        assertSame(handle, scope.getByKey("cleanup"))
        assertThrows(IllegalStateException::class.java) { service.require(TaskId.random()) }
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (scope.query() as MutableList<TaskSnapshot>).clear()
        }
    }

    @Test
    fun `an administrator cannot stop a task that forbids it but its owner can`() {
        val service = ScheduledTasks(RecordingAdapter())
        val task = service.scope(Any()).schedule(TaskSpec.builder().name("locked").cancellable(false).action { }.build())

        assertEquals(TaskControl.FORBIDDEN, service.cancelByAdmin(task.id))
        assertEquals(TaskStatus.SCHEDULED, task.status)
        assertTrue(service.query().single().let { !it.cancellable })

        task.cancel()
        assertEquals(TaskStatus.CANCELLED, task.status)
        assertEquals(TaskControl.UNAVAILABLE, service.cancelByAdmin(task.id))
    }

    @Test
    fun `run now executes a waiting repeating task once and keeps its schedule`() {
        val adapter = RecordingAdapter()
        val service = ScheduledTasks(adapter)
        var runs = 0
        val task = service.scope(Any()).schedule(
            TaskSpec.builder().name("report").interval(Duration.ofMinutes(5)).delay(Duration.ofMinutes(1)).action { runs++ }.build(),
        )
        val planned = service.query().single().nextRunAt

        assertEquals(TaskControl.DONE, service.runNow(task.id))
        adapter.fire(1)

        assertEquals(1, runs)
        val snapshot = service.query().single()
        assertEquals(TaskStatus.SCHEDULED, snapshot.status)
        assertEquals(planned, snapshot.nextRunAt)
        assertEquals(1L, snapshot.runCount)
        assertNotNull(snapshot.lastDuration)
        assertEquals(Duration.ofMinutes(5), snapshot.interval)
    }

    @Test
    fun `a task is visible unless it asks to be hidden`() {
        val service = ScheduledTasks(RecordingAdapter())
        val scope = service.scope(Any())
        scope.schedule(TaskSpec.builder().name("shown").action { }.build())
        scope.schedule(TaskSpec.builder().name("secret").hidden(true).action { }.build())

        assertEquals(mapOf("shown" to false, "secret" to true), service.query().associate { it.name to it.hidden })
    }

    @Test
    fun `run now is refused when the task forbids it`() {
        val adapter = RecordingAdapter()
        val service = ScheduledTasks(adapter)
        val task = service.scope(Any()).schedule(TaskSpec.builder().name("fixed").runnableNow(false).action { }.build())

        assertEquals(TaskControl.FORBIDDEN, service.runNow(task.id))
        assertEquals(1, adapter.requests.size)
    }

    @Test
    fun `a finished task cannot be cancelled and keeps its status`() {
        val adapter = RecordingAdapter()
        val task = ScheduledTasks(adapter).scope(Any()).schedule(spec("once"))
        adapter.fire(0)

        assertFalse(task.cancelIfActive())
        assertEquals(TaskStatus.COMPLETED, task.status)
        assertFalse(task.isCancelled)
    }

    @Test
    fun `a run the platform already queued does nothing after cancel`() {
        val adapter = RecordingAdapter()
        var runs = 0
        val task = ScheduledTasks(adapter).scope(Any())
            .schedule(TaskSpec.builder().interval(Duration.ofSeconds(1)).action { runs++ }.build())
        task.cancel()
        adapter.fire(0)

        assertEquals(0, runs)
        assertEquals(TaskStatus.CANCELLED, task.status)
    }

    @Test
    fun `a repeating task that failed does not run again`() {
        val adapter = RecordingAdapter()
        var runs = 0
        val task = ScheduledTasks(adapter).scope(Any())
            .schedule(TaskSpec.builder().interval(Duration.ofSeconds(1)).action { runs++; error("broken") }.build())
        adapter.fire(0)
        adapter.fire(0)

        assertEquals(1, runs)
        assertEquals(TaskStatus.FAILED, task.status)
    }

    private class RewardTask(private val log: MutableList<String>) : org.pnfolder.pnlibrary.api.tasks.spec.ScheduledTask() {
        override val name = "Награда"
        override val delay: Duration = Duration.ofMinutes(1)
        override val cancelWithParent = true
        override fun run(context: org.pnfolder.pnlibrary.api.tasks.run.TaskContext) { log += "reward" }
    }

    /** The examples of `docs/tasks.md`: if they stop compiling or working, the documentation is wrong. */
    @Test
    fun `a task built with the DSL starts subtasks of every kind`() {
        val adapter = RecordingAdapter()
        val scope = ScheduledTasks(adapter).scope(Any())
        val log = mutableListOf<String>()
        scope.schedule {
            name = "Ивент"
            key = "event"
            conflictPolicy = TaskConflictPolicy.REPLACE
            tag("event")
            execution = org.pnfolder.pnlibrary.api.tasks.spec.TaskExecution.global()
            interval = Duration.ofSeconds(1)
            maxLag = Duration.ofSeconds(10)
            tolerateFailures = 3
            condition { true }
            cancelWhen { false }
            whenLate(Duration.ofSeconds(1)) { log += "late" }
            onLag(Duration.ofSeconds(2)) { org.pnfolder.pnlibrary.api.tasks.run.LagResponse.RUN }
            run { ctx ->
                if (ctx.isFirstRun) {
                    ctx.spawn {
                        name = "Таймер"
                        interval = Duration.ofSeconds(1)
                        cancelWithParent = true
                        run { log += "tick" }
                    }
                    ctx.spawn(RewardTask(log))
                    ctx.later(Duration.ofSeconds(5)) { log += "later" }
                    ctx.repeat(2, Duration.ofSeconds(1)) { log += "repeat" }
                }
            }
        }

        adapter.fire(0)

        val all = scope.query(TaskQuery.all())
        val parent = all.single { it.name == "Ивент" }
        val children = all.filter { it.parentId == parent.id }
        assertEquals(setOf("Таймер", "Награда", "Ивент / later", "Ивент / repeat"), children.map { it.name }.toSet())
    }

    @Test
    fun `a ready spec can be started as a subtask many times`() {
        val adapter = RecordingAdapter()
        val scope = ScheduledTasks(adapter).scope(Any())
        val timer = TaskSpec.builder().name("Таймер").cancelWithParent(true).action { }.build()
        scope.schedule(
            TaskSpec.builder()
                .name("Ивент")
                .whenLate(Duration.ofSeconds(1)) { }
                .onLag(Duration.ofSeconds(2)) { org.pnfolder.pnlibrary.api.tasks.run.LagResponse.SKIP }
                .action { ctx -> ctx.spawn(timer); ctx.spawn(timer) }
                .build(),
        )

        adapter.fire(0)

        assertEquals(3, adapter.requests.size)
        assertEquals(2, scope.query(TaskQuery.all()).count { it.parentId != null })
    }

    private fun spec(name: String) = TaskSpec.builder().name(name).action { }.build()
    private class RecordingAdapter(
        private val fireDuringSchedule: Boolean = false,
        private val failingCancelIndex: Int? = null,
    ) : PlatformTaskAdapter {
        val requests = mutableListOf<PlatformTaskRequest>()
        val handles = mutableListOf<NativeHandle>()
        var closed = false
            private set

        override fun buildTask() = PlatformTaskBuilder(::dispatch)

        private fun dispatch(request: PlatformTaskRequest): PlatformTaskHandle {
            requests += request
            val failure = if (handles.size == failingCancelIndex) IllegalStateException("native cancellation failed") else null
            val handle = NativeHandle(failure)
            handles += handle
            if (fireDuringSchedule) request.callback.run()
            return handle
        }

        fun fire(index: Int) = requests[index].callback.run()
        override fun close() { closed = true }
    }
    private class NativeHandle(private val failure: RuntimeException? = null) : PlatformTaskHandle {
        val cancelled = AtomicBoolean()
        override fun cancel(): Boolean {
            val changed = cancelled.compareAndSet(false, true)
            if (changed) failure?.let { throw it }
            return changed
        }
    }

}
