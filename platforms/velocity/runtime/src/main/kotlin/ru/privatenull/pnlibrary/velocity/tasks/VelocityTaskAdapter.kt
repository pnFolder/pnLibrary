package ru.privatenull.pnlibrary.velocity.tasks

import com.velocitypowered.api.proxy.ProxyServer
import ru.privatenull.pnlibrary.spi.tasks.PlatformTaskAdapter
import ru.privatenull.pnlibrary.spi.tasks.PlatformTaskHandle
import ru.privatenull.pnlibrary.spi.tasks.PlatformTaskRequest
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
    private val nativeSchedule: (PlatformTaskRequest) -> PlatformTaskHandle,
) : PlatformTaskAdapter {

    /** Creates an adapter backed by Velocity's scheduler. */
    constructor(plugin: Any, server: ProxyServer) : this(
        nativeSchedule = { request ->
            val builder = server.scheduler.buildTask(plugin, request.callback)

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

    /** Schedules a request and starts tracking its cancellation handle. */
    override fun schedule(request: PlatformTaskRequest): PlatformTaskHandle {
        check(!closed.get()) { "Velocity task adapter is closed" }

        val reference = AtomicReference<TrackedHandle?>()
        val completed = AtomicBoolean(false)
        val forwardedRequest = request.withLifecycle(reference, completed)
        val handle = TrackedHandle(nativeSchedule(forwardedRequest))

        reference.set(handle)
        handles += handle

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

        handles.toList().forEach(TrackedHandle::cancel)
        handles.clear()
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

            delegate.cancel()
            handles.remove(this)
            return true
        }

        /** Removes a completed one-shot task without cancelling its native handle. */
        fun release() {
            handles.remove(this)
        }
    }
}
