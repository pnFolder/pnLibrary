package org.pnfolder.pnlibrary.core.tasks

import org.pnfolder.pnlibrary.api.tasks.TaskContext
import org.pnfolder.pnlibrary.api.tasks.TaskHandle
import org.pnfolder.pnlibrary.api.tasks.TaskId
import org.pnfolder.pnlibrary.api.tasks.TaskSnapshot
import org.pnfolder.pnlibrary.api.tasks.TaskSpec
import org.pnfolder.pnlibrary.api.tasks.TaskStatus
import org.pnfolder.pnlibrary.core.cleanup.ResourceCleanup
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskAdapter
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskHandle
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskRequest
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** One scheduled task: runs its action, tracks its state and reports to [onTerminal] once it can never run again. */
internal class ManagedTask(
    val owner: Any,
    val scopeToken: Any,
    val spec: TaskSpec,
    private val adapter: PlatformTaskAdapter,
    private val errorLogger: (Any, String, Throwable) -> Unit,
    private val onTerminal: (ManagedTask) -> Unit,
) : TaskHandle {
    override val id = TaskId.random()
    private val state = AtomicReference(TaskStatus.SCHEDULED)
    private val running = AtomicBoolean(false)
    private val runs = AtomicLong()
    private val skipped = AtomicLong()
    private val cancelled = AtomicBoolean(false)
    private val native = AtomicReference<PlatformTaskHandle?>()
    private val created = Instant.now()
    @Volatile
    private var nextRun: Instant? = created.plus(spec.delay)

    @Volatile
    private var started: Instant? = null

    @Volatile
    private var completed: Instant? = null

    @Volatile
    private var failure: String? = null

    @Volatile
    private var lastDuration: Duration? = null
    override val status get() = state.get()
    override val isCancelled get() = cancelled.get()

    fun attach(handle: PlatformTaskHandle) {
        native.set(handle)
        if (cancelled.get() || status == TaskStatus.COMPLETED || status == TaskStatus.FAILED) {
            handle.cancel()
        }
    }

    fun invoke() = execute(manual = false)

    /** Starts a waiting task now, on its own execution context, and leaves its schedule as it was. */
    fun runNow(): Boolean {
        if (cancelled.get() || status != TaskStatus.SCHEDULED) return false
        adapter.schedule(
            PlatformTaskRequest(spec.execution.kind, spec.execution.target, Duration.ZERO, null, Runnable { execute(manual = true) }, owner = owner),
        )
        return true
    }

    private fun execute(manual: Boolean) {
        if (cancelled.get()) return
        if (!running.compareAndSet(false, true)) {
            if (!manual) skipped.incrementAndGet()
            return
        }
        try {
            val plannedNext = nextRun
            if (!manual) nextRun = null
            if (!manual && spec.cancellationConditions.any { it.asBoolean }) {
                cancel()
                return
            }
            if (!manual && spec.conditions.any { !it.asBoolean }) return skip()
            runAction()
            if (!cancelled.get()) finishRun(manual, plannedNext)
        } catch (error: Throwable) {
            fail(error)
        } finally {
            running.set(false)
        }
    }

    /** A condition said no: the run is skipped, and a one-time task is over. */
    private fun skip() {
        skipped.incrementAndGet()
        if (spec.interval == null) complete() else nextRun = Instant.now().plus(spec.interval)
    }

    private fun runAction() {
        state.set(TaskStatus.RUNNING)
        val runStartedAt = Instant.now()
        started = runStartedAt
        val number = runs.incrementAndGet()
        val clock = System.nanoTime()
        spec.action.run(object : TaskContext {
            override val id = this@ManagedTask.id
            override val name = spec.name
            override val runNumber = number
            override val scheduledAt = created.plus(spec.delay)
            override val startedAt = runStartedAt
            override fun cancel() = this@ManagedTask.cancel()
        })
        lastDuration = Duration.ofNanos(System.nanoTime() - clock)
    }

    /** After a run: a one-time task completes, a repeating one waits for its next time. */
    private fun finishRun(manual: Boolean, plannedNext: Instant?) {
        if (spec.interval == null) {
            complete()
            if (manual) native.get()?.cancel()
        } else {
            state.set(TaskStatus.SCHEDULED)
            nextRun = if (manual) plannedNext else Instant.now().plus(spec.interval)
        }
    }

    private fun fail(error: Throwable) {
        failure = "${error.javaClass.simpleName}: ${error.message.orEmpty()}"
        state.set(TaskStatus.FAILED)
        completed = Instant.now()
        ResourceCleanup.suppressInto(
            error,
            { native.get()?.cancel() },
            { errorLogger(owner, "[pnLibrary/tasks] Task ${spec.name ?: id} failed", error) },
        )
        terminal()
    }

    private fun complete() {
        state.set(TaskStatus.COMPLETED)
        completed = Instant.now()
        terminal()
    }

    override fun cancel() { cancelIfActive() }

    override fun cancelIfActive(): Boolean {
        if (!cancelled.compareAndSet(false, true)) return false
        state.set(TaskStatus.CANCELLED)
        completed = Instant.now()
        try {
            native.get()?.cancel()
        } finally {
            terminal()
        }
        return true
    }

    override fun snapshot() = TaskSnapshot(
        id = id,
        name = spec.name,
        key = spec.key,
        ownerName = ownerName(owner),
        executionKind = spec.execution.kind,
        status = status,
        tags = spec.tags,
        createdAt = created,
        nextRunAt = nextRun,
        runCount = runs.get(),
        skippedCount = skipped.get(),
        lastStartedAt = started,
        lastCompletedAt = completed,
        lastFailure = failure,
        description = spec.description,
        usage = spec.usage,
        source = sourceOf(spec.action.javaClass),
        interval = spec.interval,
        lastDuration = lastDuration,
        cancellable = spec.cancellable,
        runnableNow = spec.runnableNow,
        hidden = spec.hidden,
    )

    private fun terminal() = onTerminal(this)
}

/** The class that holds the action; a lambda reports the class it was written in. */
private fun sourceOf(type: Class<*>): String = type.name.substringBefore("\$\$Lambda").substringBefore('/')

private fun ownerName(owner: Any) = owner.javaClass.simpleName.ifBlank { owner.toString() }
