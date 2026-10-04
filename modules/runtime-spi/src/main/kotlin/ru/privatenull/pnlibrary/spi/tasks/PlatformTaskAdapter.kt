package ru.privatenull.pnlibrary.spi.tasks

import ru.privatenull.pnlibrary.api.tasks.TaskExecution
import java.time.Duration

/**
 * Native scheduling request emitted by the shared task service.
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

/** Native cancellation handle returned by a platform scheduler. */
fun interface PlatformTaskHandle {
    /** Cancels future execution and reports whether cancellation changed state. */
    fun cancel(): Boolean
}

/** Native scheduling boundary implemented by each platform runtime. */
interface PlatformTaskAdapter : AutoCloseable {
    /** Schedules [request] and returns its native cancellation handle. */
    fun schedule(request: PlatformTaskRequest): PlatformTaskHandle
    /** Releases platform scheduler resources. */
    override fun close() = Unit
}

/** Adapter used by runtimes that do not provide native task scheduling. */
object UnsupportedPlatformTaskAdapter : PlatformTaskAdapter {
    /** Always fails because scheduling is unavailable. */
    override fun schedule(request: PlatformTaskRequest): PlatformTaskHandle =
        throw UnsupportedOperationException("Native task scheduling is not supported by this platform")
}
