package ru.privatenull.pnlibrary.bukkit.tasks

import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitScheduler
import org.bukkit.scheduler.BukkitTask
import ru.privatenull.pnlibrary.api.tasks.TaskExecution
import ru.privatenull.pnlibrary.bukkit.compat.ServerCapabilities
import ru.privatenull.pnlibrary.spi.tasks.PlatformTaskAdapter
import ru.privatenull.pnlibrary.spi.tasks.PlatformTaskHandle
import ru.privatenull.pnlibrary.spi.tasks.PlatformTaskRequest
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.ceil

private const val NANOS_PER_TICK = 50_000_000.0

internal fun durationToTicks(value: Duration): Long =
    if (value.isZero) 0 else ceil(value.toNanos() / NANOS_PER_TICK).toLong().coerceAtLeast(1)

internal fun withCompletionRelease(request: PlatformTaskRequest, release: () -> Unit): PlatformTaskRequest =
    request.copy(callback = Runnable {
        try {
            request.callback.run()
        } finally {
            if (request.interval == null) release()
        }
    })

internal class BukkitTaskAdapter(private val plugin: Plugin) : PlatformTaskAdapter {
    private val foliaScheduler by lazy { FoliaSchedulerBridge(plugin) }
    private val closed = AtomicBoolean(false)
    private val handles = ConcurrentHashMap.newKeySet<NativeHandle>()

    override fun schedule(request: PlatformTaskRequest): PlatformTaskHandle {
        check(!closed.get()) { "Bukkit task adapter is closed" }
        val reference = AtomicReference<NativeHandle?>()
        val completed = AtomicBoolean(false)
        val forwarded = withCompletionRelease(request) {
            completed.set(true)
            reference.get()?.release()
        }
        val native = if (ServerCapabilities.isFolia) foliaScheduler.schedule(forwarded) else scheduleBukkit(forwarded)
        return NativeHandle(native).also { handle ->
            reference.set(handle)
            handles += handle
            if (completed.get()) {
                handle.release()
            }
        }
    }

    private fun scheduleBukkit(request: PlatformTaskRequest): Any {
        val scheduler = Bukkit.getScheduler()
        val delay = durationToTicks(request.delay)
        val period = request.interval?.let(::durationToTicks)

        return if (request.executionKind == TaskExecution.Kind.ASYNC) {
            scheduleBukkitAsync(scheduler, request.callback, delay, period)
        } else {
            scheduleBukkitGlobal(scheduler, request.callback, delay, period)
        }
    }

    private fun scheduleBukkitAsync(
        scheduler: BukkitScheduler,
        callback: Runnable,
        delay: Long,
        period: Long?,
    ): Any = when {
        period != null -> scheduler.runTaskTimerAsynchronously(plugin, callback, delay, period)
        delay > 0 -> scheduler.runTaskLaterAsynchronously(plugin, callback, delay)
        else -> scheduler.runTaskAsynchronously(plugin, callback)
    }

    private fun scheduleBukkitGlobal(
        scheduler: BukkitScheduler,
        callback: Runnable,
        delay: Long,
        period: Long?,
    ): Any = when {
        period != null -> scheduler.runTaskTimer(plugin, callback, delay, period)
        delay > 0 -> scheduler.runTaskLater(plugin, callback, delay)
        else -> scheduler.runTask(plugin, callback)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        handles.toList().forEach { it.cancel() }
        handles.clear()
    }

    private inner class NativeHandle(private val native: Any) : PlatformTaskHandle {
        private val cancelled = AtomicBoolean(false)
        override fun cancel(): Boolean {
            if (!cancelled.compareAndSet(false, true)) return false
            when (native) {
                is BukkitTask -> native.cancel()
                else -> foliaScheduler.cancel(native)
            }
            handles.remove(this)
            return true
        }
        fun release() {
            handles.remove(this)
        }
    }
}
