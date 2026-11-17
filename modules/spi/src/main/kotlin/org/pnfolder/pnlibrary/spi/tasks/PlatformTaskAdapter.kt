package org.pnfolder.pnlibrary.spi.tasks

import org.pnfolder.pnlibrary.api.tasks.TaskExecution
import java.time.Duration

/** Native cancellation handle returned by a platform scheduler. */
fun interface PlatformTaskHandle {
    /** Cancels future execution and reports whether cancellation changed state. */
    fun cancel(): Boolean
}

/**
 * Native scheduling request produced by [PlatformTaskBuilder].
 *
 * @property executionKind requested platform execution context
 * @property target native entity target for entity execution
 * @property delay delay before the first invocation
 * @property interval repeat interval, or `null` for one-shot execution
 * @property callback callback dispatched by the platform scheduler
 */
data class PlatformTaskRequest(
    val executionKind: TaskExecution.Kind,
    val target: Any?,
    val delay: Duration,
    val interval: Duration?,
    val callback: Runnable,
) {
    init {
        require(executionKind != TaskExecution.Kind.ENTITY || target != null) { "Entity task target is required" }
        require(!delay.isNegative) { "Task delay must not be negative" }
        interval?.let { require(!it.isNegative && !it.isZero) { "Task interval must be positive" } }
    }
}

/**
 * Fluent description of a task, started with [PlatformTaskAdapter.buildTask].
 *
 * Unless changed the task is global, starts immediately and runs once:
 * ```
 * adapter.buildTask()
 *     .task(callback)
 *     .delay(Duration.ofSeconds(5))
 *     .every(Duration.ofSeconds(30))
 *     .schedule()
 * ```
 *
 * @param dispatch hands the finished request to the platform scheduler
 */
class PlatformTaskBuilder(private val dispatch: (PlatformTaskRequest) -> PlatformTaskHandle) {
    private var callback: Runnable? = null
    private var kind = TaskExecution.Kind.GLOBAL
    private var target: Any? = null
    private var delay = Duration.ZERO
    private var interval: Duration? = null

    /** Sets the [callback] the task runs; required. */
    fun task(callback: Runnable) = apply { this.callback = callback }

    /** Runs the task in the execution context of the entity [target]. */
    fun entity(target: Any) = execution(TaskExecution.Kind.ENTITY, target)

    /** Runs the task in the given execution [kind], bound to [target] when the kind needs one. */
    fun execution(kind: TaskExecution.Kind, target: Any? = null) = apply {
        this.kind = kind
        this.target = target
    }

    /** Waits [delay] before the first run. */
    fun delay(delay: Duration) = apply { this.delay = delay }

    /** Repeats the task every [interval]. */
    fun every(interval: Duration) = apply { this.interval = interval }

    /** Copies every setting from an already built [request]. */
    fun from(request: PlatformTaskRequest) = apply {
        callback = request.callback
        kind = request.executionKind
        target = request.target
        delay = request.delay
        interval = request.interval
    }

    /** Builds the request without scheduling it. */
    fun build() = PlatformTaskRequest(
        kind, target, delay, interval, checkNotNull(callback) { "Task callback is not set" },
    )

    /** Schedules the task and returns its native cancellation handle. */
    fun schedule(): PlatformTaskHandle = dispatch(build())
}

/** Native scheduling boundary implemented by each platform runtime. */
interface PlatformTaskAdapter : AutoCloseable {
    /** Starts describing a task; finish with [PlatformTaskBuilder.schedule]. */
    fun buildTask(): PlatformTaskBuilder

    /** Releases platform scheduler resources. */
    override fun close() = Unit
}

/** Adapter used by runtimes that do not provide native task scheduling. */
object UnsupportedPlatformTaskAdapter : PlatformTaskAdapter {
    /** Returns a builder whose [PlatformTaskBuilder.schedule] always fails. */
    override fun buildTask() = PlatformTaskBuilder {
        throw UnsupportedOperationException("Native task scheduling is not supported by this platform")
    }
}
