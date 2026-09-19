package ru.privatenull.pnlibrary.velocity.tasks

import com.velocitypowered.api.proxy.ProxyServer
import ru.privatenull.pnlibrary.spi.tasks.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal class VelocityTaskAdapter internal constructor(
    private val nativeSchedule: (PlatformTaskRequest) -> PlatformTaskHandle,
) : PlatformTaskAdapter {
    constructor(plugin: Any, server: ProxyServer) : this({ request ->
        val builder = server.scheduler.buildTask(plugin, request.callback)
        if (!request.delay.isZero) builder.delay(request.delay)
        request.interval?.let(builder::repeat)
        val task = builder.schedule()
        PlatformTaskHandle { task.cancel(); true }
    })

    private val closed = AtomicBoolean(false)
    private val handles = ConcurrentHashMap.newKeySet<TrackedHandle>()
    override fun schedule(request: PlatformTaskRequest): PlatformTaskHandle {
        check(!closed.get()) { "Velocity task adapter is closed" }
        val reference = AtomicReference<TrackedHandle?>()
        val completed = AtomicBoolean(false)
        val forwarded = request.copy(callback = Runnable {
            try { request.callback.run() } finally {
                if (request.interval == null) { completed.set(true); reference.get()?.release() }
            }
        })
        return TrackedHandle(nativeSchedule(forwarded)).also {
            reference.set(it); handles += it; if (completed.get()) it.release()
        }
    }
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        handles.toList().forEach { it.cancel() }; handles.clear()
    }
    private inner class TrackedHandle(private val delegate: PlatformTaskHandle) : PlatformTaskHandle {
        private val cancelled = AtomicBoolean(false)
        override fun cancel(): Boolean {
            if (!cancelled.compareAndSet(false, true)) return false
            delegate.cancel(); handles.remove(this); return true
        }
        fun release() { handles.remove(this) }
    }
}
