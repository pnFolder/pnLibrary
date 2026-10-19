package org.pnfolder.pnlibrary.spi.tasks

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Shared lifecycle wrapper for native task adapters.
 *
 * Platform implementations only translate a request to a native handle; this class owns
 * closed-state checks, one-shot release, idempotent cancellation, and bulk shutdown.
 */
abstract class TrackingPlatformTaskAdapter : PlatformTaskAdapter {
    private val closed = AtomicBoolean(false)
    private val handles = ConcurrentHashMap.newKeySet<TrackedHandle>()

    protected abstract fun scheduleNative(request: PlatformTaskRequest): PlatformTaskHandle

    final override fun schedule(request: PlatformTaskRequest): PlatformTaskHandle {
        check(!closed.get()) { "Platform task adapter is closed" }
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
        return TrackedHandle(scheduleNative(forwarded)).also { handle ->
            reference.set(handle)
            handles += handle
            when {
                closed.get() -> handle.cancel()
                completed.get() -> handle.release()
            }
        }
    }

    final override fun close() {
        if (!closed.compareAndSet(false, true)) return
        var failure: Throwable? = null
        handles.toList().forEach { handle ->
            try {
                handle.cancel()
            } catch (error: Throwable) {
                if (failure == null) failure = error
            }
        }
        handles.clear()
        failure?.let { throw it }
    }

    private inner class TrackedHandle(
        private val delegate: PlatformTaskHandle,
    ) : PlatformTaskHandle {
        private val cancelled = AtomicBoolean(false)

        override fun cancel(): Boolean {
            if (!cancelled.compareAndSet(false, true)) return false
            delegate.cancel()
            handles.remove(this)
            return true
        }

        fun release() {
            handles.remove(this)
        }
    }
}
