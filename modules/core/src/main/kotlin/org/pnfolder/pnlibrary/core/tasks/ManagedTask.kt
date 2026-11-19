package org.pnfolder.pnlibrary.core.tasks

import org.pnfolder.pnlibrary.api.tasks.run.LagResponse
import org.pnfolder.pnlibrary.api.tasks.run.TaskContext
import org.pnfolder.pnlibrary.api.tasks.TaskHandle
import org.pnfolder.pnlibrary.api.tasks.info.TaskId
import org.pnfolder.pnlibrary.api.tasks.info.TaskLag
import org.pnfolder.pnlibrary.api.tasks.info.TaskSnapshot
import org.pnfolder.pnlibrary.api.tasks.spec.TaskSpec
import org.pnfolder.pnlibrary.api.tasks.info.TaskStatus
import org.pnfolder.pnlibrary.core.exceptions.ResourceCleanup
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskAdapter
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskHandle
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
    private val spawn: (TaskSpec, TaskId) -> TaskHandle = { _, _ -> error("This task cannot start other tasks") },
    private val parentId: TaskId? = null,
) : TaskHandle {
    override val id = TaskId.random()
    private val state = AtomicReference(TaskStatus.SCHEDULED)
    private val running = AtomicBoolean(false)
    private val runs = AtomicLong()
    private val skipped = AtomicLong()
    private val failedRuns = AtomicLong()
    private val failureStreak = AtomicLong()
    private val linkedChildren = java.util.concurrent.ConcurrentLinkedQueue<TaskHandle>()
    private val cancelled = AtomicBoolean(false)
    private val native = AtomicReference<PlatformTaskHandle?>()
    private val created = Instant.now()

    @Volatile private var nextRun: Instant? = created.plus(spec.delay)

    @Volatile private var started: Instant? = null

    @Volatile private var completed: Instant? = null

    @Volatile private var failure: String? = null

    @Volatile private var lastDuration: Duration? = null

    @Volatile private var lastLag: Duration? = null

    @Volatile private var worstLag: Duration? = null

    override val status: TaskStatus get() = state.get()
    override val isCancelled: Boolean get() = cancelled.get()

    fun attach(handle: PlatformTaskHandle) {
        native.set(handle)
        if (status.isTerminal) handle.cancel()
    }

    /** The run the platform scheduled. */
    fun invoke() = exclusively(onBusy = { skipped.incrementAndGet() }) {
        val planned = nextRun ?: created.plus(spec.delay)
        nextRun = null
        when {
            spec.cancellationConditions.any { it.asBoolean } -> cancel()
            spec.conditions.any { !it.asBoolean } -> skip()
            tooLate(planned) -> skip()
            else -> when (lagResponse(planned)) {
                LagResponse.CANCEL -> cancel()
                LagResponse.SKIP -> skip()
                LagResponse.RUN -> {
                    runAction(planned)
                    if (spec.interval == null) complete() else waitUntil(Instant.now().plus(spec.interval))
                }
            }
        }
    }

    /** Starts a waiting task now, on its own execution context, and leaves its schedule as it was. */
    fun runNow(): Boolean {
        if (status != TaskStatus.SCHEDULED) return false
        adapter.buildTask()
            .task { runRequested() }
            .execution(spec.execution.kind, spec.execution.target)
            .schedule()
        return true
    }

    private fun runRequested() = exclusively(onBusy = {}) {
        val plannedNext = nextRun
        runAction(Instant.now())
        if (spec.interval != null) return@exclusively waitUntil(plannedNext)
        complete()
        native.get()?.cancel()
    }

    /** Runs [body] unless the task is over; while another run is still going, calls [onBusy] instead. */
    private inline fun exclusively(onBusy: () -> Unit, body: () -> Unit) {
        if (status.isTerminal) return
        if (!running.compareAndSet(false, true)) return onBusy()
        try {
            body()
        } catch (error: Throwable) {
            failedRuns.incrementAndGet()
            if (!survive(error)) fail(error)
        } finally {
            running.set(false)
        }
    }

    /** A condition said no: the run is skipped, and a one-time task is over. */
    private fun skip() {
        skipped.incrementAndGet()
        if (spec.interval == null) complete() else nextRun = Instant.now().plus(spec.interval)
    }

    /** Notes how late the run is and lets the lag handler decide what happens to it. */
    private fun lagResponse(planned: Instant): LagResponse {
        val report = TaskLag(id, spec.name, runs.get() + 1, planned, Instant.now(), spec.interval)
        lastLag = report.lag
        if (worstLag.let { it == null || report.lag > it }) worstLag = report.lag
        val handler = spec.lagHandler ?: return LagResponse.RUN
        if (!report.isOver(spec.lagThreshold)) return LagResponse.RUN
        return try {
            handler.handle(report)
        } catch (error: Throwable) {
            runCatching { errorLogger(owner, "[pnLibrary/tasks] Lag handler of task ${spec.name ?: id} failed", error) }
            LagResponse.RUN
        }
    }

    /** Whether a run planned for [planned] started later than the task allows. */
    private fun tooLate(planned: Instant): Boolean =
        spec.maxLag?.let { Duration.between(planned, Instant.now()) > it } == true

    /** A repeating task may survive some failures in a row: the failure is logged and the next interval tries again. */
    private fun survive(error: Throwable): Boolean {
        val interval = spec.interval ?: return false
        val streak = failureStreak.incrementAndGet()
        if (streak > spec.tolerateFailures) return false
        failure = describe(error)
        runCatching {
            errorLogger(owner, "[pnLibrary/tasks] Task ${spec.name ?: id} failed ($streak of ${spec.tolerateFailures} tolerated), trying again", error)
        }.onFailure(error::addSuppressed)
        waitUntil(Instant.now().plus(interval))
        return true
    }

    private fun describe(error: Throwable) = "${error.javaClass.simpleName}: ${error.message.orEmpty()}"

    private fun runAction(plannedAt: Instant) {
        if (!state.compareAndSet(TaskStatus.SCHEDULED, TaskStatus.RUNNING)) return
        val previousStart = started
        val runStartedAt = Instant.now()
        started = runStartedAt
        val number = runs.incrementAndGet()
        val clock = System.nanoTime()
        spec.action.run(object : TaskContext {
            override val id = this@ManagedTask.id
            override val name = spec.name
            override val key = spec.key
            override val tags = spec.tags
            override val executionKind = spec.execution.kind
            override val parentId = this@ManagedTask.parentId
            override val execution = spec.execution
            override val interval = spec.interval
            override val runNumber = number
            override val skippedCount get() = skipped.get()
            override val scheduledAt = plannedAt
            override val startedAt = runStartedAt
            override val previousStartedAt = previousStart
            override val isCancelled get() = cancelled.get()
            override fun cancel() = this@ManagedTask.cancel()
            override fun snapshot() = this@ManagedTask.snapshot()
            override fun spawn(spec: TaskSpec) = spawnChild(spec)
        })
        failureStreak.set(0)
        lastDuration = Duration.ofNanos(System.nanoTime() - clock)
    }

    /** Starts [child] under this task; a child that asked for it is cancelled when this task is. */
    private fun spawnChild(child: TaskSpec): TaskHandle {
        val handle = spawn(child, id)
        if (child.cancelWithParent) {
            linkedChildren.removeIf { it.status.isTerminal }
            linkedChildren += handle
            if (cancelled.get()) handle.cancel()
        }
        return handle
    }

    private fun waitUntil(next: Instant?) {
        if (state.compareAndSet(TaskStatus.RUNNING, TaskStatus.SCHEDULED)) nextRun = next
    }

    private fun fail(error: Throwable) {
        val failed = end(TaskStatus.FAILED)
        if (failed) failure = describe(error)
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
            linkedChildren.forEach(TaskHandle::cancel)
            onTerminal(this)
        }
        return true
    }

    /** Moves the task to the final [status]; false when it was already over. */
    private fun end(status: TaskStatus): Boolean {
        val previous = state.getAndUpdate { if (it.isTerminal) it else status }
        if (previous.isTerminal) return false
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
        failedCount = failedRuns.get(),
        parentId = parentId,
        lastLag = lastLag,
        worstLag = worstLag,
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

/** The class that holds the action; a lambda reports the class it was written in. */
private fun sourceOf(type: Class<*>): String = type.name.substringBefore("\$\$Lambda").substringBefore('/')

private fun ownerName(owner: Any) = owner.javaClass.simpleName.ifBlank { owner.toString() }
