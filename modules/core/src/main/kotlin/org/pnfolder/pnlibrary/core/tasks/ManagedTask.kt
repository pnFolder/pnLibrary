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

/**
 * One scheduled task: runs its action, tracks its state and reports to [onTerminal] once it can never run again.
 *
 * Every change of the state is a compare-and-set, so a cancel racing with a run is never overwritten when the run
 * ends, and a run the platform had already queued does nothing once the task is over.
 */
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

    @Volatile private var nextRun: Instant? = created.plus(spec.delay)

    @Volatile private var started: Instant? = null

    @Volatile private var completed: Instant? = null

    @Volatile private var failure: String? = null

    @Volatile private var lastDuration: Duration? = null

    override val status: TaskStatus get() = state.get()
    override val isCancelled: Boolean get() = cancelled.get()

    fun attach(handle: PlatformTaskHandle) {
        native.set(handle)
        if (status.isOver()) handle.cancel()
    }

    /** The run the platform scheduled. */
    fun invoke() = exclusively(onBusy = { skipped.incrementAndGet() }) {
        nextRun = null
        when {
            spec.cancellationConditions.any { it.asBoolean } -> cancel()
            spec.conditions.any { !it.asBoolean } -> skip()
            else -> {
                runAction()
                if (spec.interval == null) complete() else waitUntil(Instant.now().plus(spec.interval))
            }
        }
    }

    /** Starts a waiting task now, on its own execution context, and leaves its schedule as it was. */
    fun runNow(): Boolean {
        if (status != TaskStatus.SCHEDULED) return false
        adapter.schedule(
            PlatformTaskRequest(spec.execution.kind, spec.execution.target, Duration.ZERO, null, Runnable { runRequested() }, owner = owner),
        )
        return true
    }

    private fun runRequested() = exclusively(onBusy = {}) {
        val plannedNext = nextRun
        runAction()
        if (spec.interval != null) return@exclusively waitUntil(plannedNext)
        complete()
        native.get()?.cancel()
    }

    /** Runs [body] unless the task is over; while another run is still going, calls [onBusy] instead. */
    private inline fun exclusively(onBusy: () -> Unit, body: () -> Unit) {
        if (status.isOver()) return
        if (!running.compareAndSet(false, true)) return onBusy()
        try {
            body()
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
        if (!state.compareAndSet(TaskStatus.SCHEDULED, TaskStatus.RUNNING)) return
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

    private fun waitUntil(next: Instant?) {
        if (state.compareAndSet(TaskStatus.RUNNING, TaskStatus.SCHEDULED)) nextRun = next
    }

    private fun fail(error: Throwable) {
        val failed = end(TaskStatus.FAILED)
        if (failed) failure = "${error.javaClass.simpleName}: ${error.message.orEmpty()}"
        ResourceCleanup.suppressInto(
            error,
            { native.get()?.cancel() },
            { errorLogger(owner, "[pnLibrary/tasks] Task ${spec.name ?: id} failed", error) },
        )
        if (failed) onTerminal(this)
    }

    private fun complete() {
        if (end(TaskStatus.COMPLETED)) onTerminal(this)
    }

    override fun cancel() {
        cancelIfActive()
    }

    override fun cancelIfActive(): Boolean {
        if (!end(TaskStatus.CANCELLED)) return false
        cancelled.set(true)
        try {
            native.get()?.cancel()
        } finally {
            onTerminal(this)
        }
        return true
    }

    /** Moves the task to the final [status]; false when it was already over. */
    private fun end(status: TaskStatus): Boolean {
        val previous = state.getAndUpdate { if (it.isOver()) it else status }
        if (previous.isOver()) return false
        completed = Instant.now()
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
}

private fun TaskStatus.isOver() = this == TaskStatus.COMPLETED || this == TaskStatus.CANCELLED || this == TaskStatus.FAILED

/** The class that holds the action; a lambda reports the class it was written in. */
private fun sourceOf(type: Class<*>): String = type.name.substringBefore("\$\$Lambda").substringBefore('/')

private fun ownerName(owner: Any) = owner.javaClass.simpleName.ifBlank { owner.toString() }
