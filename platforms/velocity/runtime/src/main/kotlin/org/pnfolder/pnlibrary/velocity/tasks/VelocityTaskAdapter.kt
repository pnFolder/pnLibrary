package org.pnfolder.pnlibrary.velocity.tasks

import com.velocitypowered.api.proxy.ProxyServer
import org.pnfolder.pnlibrary.api.tasks.TaskExecution
import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskAdapter
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskHandle
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskRequest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Adapts pnLibrary task requests to the native Velocity scheduler.
 *
 * The adapter owns every scheduled handle and cancels all remaining tasks when it is closed.
 * One-shot tasks are removed from the tracking set immediately after their callback finishes.
 */
internal class VelocityTaskAdapter internal constructor(
    private val describeOwner: (Any?) -> String,
    private val warning: (String) -> Unit,
    private val nativeSchedule: (PlatformTaskRequest) -> PlatformTaskHandle,
) : PlatformTaskAdapter {

    /**
     * Creates an adapter backed by Velocity's scheduler. [describeOwner] names the plugin that asked for a task,
     * and [warning] receives a notice every time an entity task is run on the proxy scheduler instead.
     */
    constructor(server: ProxyServer, describeOwner: (Any?) -> String, warning: (String) -> Unit) : this(
        describeOwner = describeOwner,
        warning = warning,
        nativeSchedule = { request ->
            val owner = requireNotNull(request.owner) { "Velocity tasks require a native plugin owner" }
            require(server.pluginManager.fromInstance(owner).isPresent) {
                "Task owner must be a registered Velocity plugin"
            }
            val builder = server.scheduler.buildTask(owner, request.callback)

            if (!request.delay.isZero) {
                builder.delay(request.delay)
            }
            request.interval?.let(builder::repeat)

            val task = builder.schedule()
            PlatformTaskHandle {
                task.cancel()
                true
            }
        },
    )

    private val closed = AtomicBoolean(false)
    private val handles = ConcurrentHashMap.newKeySet<TrackedHandle>()

    /**
     * Schedules a request and starts tracking its cancellation handle.
     *
     * Velocity has no entity scheduler, so an entity task runs on the proxy scheduler instead, and every such
     * call writes a warning that names the plugin which asked for it.
     */
    override fun schedule(request: PlatformTaskRequest): PlatformTaskHandle {
        check(!closed.get()) { "Velocity task adapter is closed" }
        if (request.executionKind == TaskExecution.Kind.ENTITY) {
            warning(
                "Плагин ${describeOwner(request.owner)} запросил задачу, привязанную к игроку. " +
                    "Velocity такие задачи не поддерживает: задача выполнена на общем планировщике прокси.",
            )
        }

        val reference = AtomicReference<TrackedHandle?>()
        val completed = AtomicBoolean(false)
        val forwardedRequest = request.withLifecycle(reference, completed)
        val handle = TrackedHandle(nativeSchedule(forwardedRequest))

        reference.set(handle)
        handles += handle

        if (closed.get()) {
            handle.cancel()
            error("Velocity task adapter is closed")
        }

        if (completed.get()) {
            handle.release()
        }

        return handle
    }

    /** Cancels all tasks still owned by this adapter. */
    override fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }

        var failure: Throwable? = null
        handles.toList().forEach { handle ->
            runCatchingNonFatal { handle.cancel() }.onFailure { error ->
                if (failure == null) failure = error
                else if (failure !== error) failure?.addSuppressed(error)
            }
        }
        handles.clear()
        failure?.let { throw it }
    }

    /** Wraps a request callback so completed one-shot tasks can be untracked safely. */
    private fun PlatformTaskRequest.withLifecycle(
        reference: AtomicReference<TrackedHandle?>,
        completed: AtomicBoolean,
    ): PlatformTaskRequest = copy(
        callback = Runnable {
            try {
                callback.run()
            } finally {
                if (interval == null) {
                    completed.set(true)
                    reference.get()?.release()
                }
            }
        },
    )

    /** Tracks a native handle and makes cancellation idempotent. */
    private inner class TrackedHandle(
        private val delegate: PlatformTaskHandle,
    ) : PlatformTaskHandle {
        private val cancelled = AtomicBoolean(false)

        /** Cancels future invocations once and removes the task from tracking. */
        override fun cancel(): Boolean {
            if (!cancelled.compareAndSet(false, true)) {
                return false
            }

            try {
                delegate.cancel()
            } finally {
                handles.remove(this)
            }
            return true
        }

        /** Removes a completed one-shot task without cancelling its native handle. */
        fun release() {
            handles.remove(this)
        }
    }
}
