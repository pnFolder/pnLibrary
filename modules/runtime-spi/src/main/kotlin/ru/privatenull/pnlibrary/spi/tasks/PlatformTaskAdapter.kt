package ru.privatenull.pnlibrary.spi.tasks

import ru.privatenull.pnlibrary.api.tasks.TaskExecution
import java.time.Duration

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

fun interface PlatformTaskHandle { fun cancel(): Boolean }

interface PlatformTaskAdapter : AutoCloseable {
    fun schedule(request: PlatformTaskRequest): PlatformTaskHandle
    override fun close() = Unit
}

object UnsupportedPlatformTaskAdapter : PlatformTaskAdapter {
    override fun schedule(request: PlatformTaskRequest): PlatformTaskHandle =
        throw UnsupportedOperationException("Native task scheduling is not supported by this platform")
}
