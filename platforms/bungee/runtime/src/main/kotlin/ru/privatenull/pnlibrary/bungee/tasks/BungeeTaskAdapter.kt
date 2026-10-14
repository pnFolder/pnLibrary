package ru.privatenull.pnlibrary.bungee.tasks

import net.md_5.bungee.api.plugin.Plugin
import ru.privatenull.pnlibrary.spi.tasks.PlatformTaskAdapter
import ru.privatenull.pnlibrary.spi.tasks.PlatformTaskHandle
import ru.privatenull.pnlibrary.spi.tasks.PlatformTaskRequest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Adapts portable task requests to BungeeCord's asynchronous scheduler.
 *
 * BungeeCord has no main-thread or entity-region scheduler. Every execution kind uses the
 * same native scheduler; completed one-shot tasks are released from lifecycle tracking.
 */
internal class BungeeTaskAdapter internal constructor(
    private val nativeSchedule: (PlatformTaskRequest) -> PlatformTaskHandle,
) : PlatformTaskAdapter {
    /** Creates an adapter backed by the owning plugin's scheduler. */
    constructor(plugin: Plugin) : this({ request ->
        val scheduler = plugin.proxy.scheduler
        val interval = request.interval
        val task = if (interval == null) {
            scheduler.schedule(
                plugin,
                request.callback,
                request.delay.toMillis(),
                TimeUnit.MILLISECONDS,
            )
        } else {
            scheduler.schedule(
                plugin,
                request.callback,
                request.delay.toMillis(),
                interval.toMillis(),
                TimeUnit.MILLISECONDS,
            )
        }

        PlatformTaskHandle {
            task.cancel()
            true
        }
    })

    private val closed = AtomicBoolean(false)
    private val handles = ConcurrentHashMap.newKeySet<TrackedHandle>()

    /** Schedules a callback and tracks its handle until completion or cancellation. */
    override fun schedule(request: PlatformTaskRequest): PlatformTaskHandle {
        check(!closed.get()) { "Bungee task adapter is closed" }
        val reference = AtomicReference<TrackedHandle?>()
        val completed = AtomicBoolean(false)
        val forwarded = request.copy(callback = Runnable {
            try {
                request.callback.run()
            } finally {
                if (request.interval == null) {
                    completed.set(true)
                    reference.get()?.release()
                }
            }
        })
        return TrackedHandle(nativeSchedule(forwarded)).also { handle ->
            reference.set(handle)
            handles += handle
            if (completed.get()) {
                handle.release()
            }
        }
    }

    /** Cancels all tasks still owned by this adapter. Repeated calls have no effect. */
    override fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        handles.toList().forEach(TrackedHandle::cancel)
        handles.clear()
    }

    /** Removes the native task from tracking and makes cancellation idempotent. */
    private inner class TrackedHandle(
        private val delegate: PlatformTaskHandle,
    ) : PlatformTaskHandle {
        private val cancelled = AtomicBoolean(false)

        /** Cancels the native task at most once. */
        override fun cancel(): Boolean {
            if (!cancelled.compareAndSet(false, true)) {
                return false
            }
            delegate.cancel()
            handles.remove(this)
            return true
        }

        /** Untracks a completed callback without cancelling its native handle. */
        fun release() {
            handles.remove(this)
        }
    }
}
