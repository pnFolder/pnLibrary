package org.pnfolder.pnlibrary.bukkit.tasks

import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitScheduler
import org.bukkit.scheduler.BukkitTask
import org.pnfolder.pnlibrary.api.tasks.spec.TaskExecution
import org.pnfolder.pnlibrary.bukkit.compat.ServerCapabilities
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskHandle
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskRequest
import org.pnfolder.pnlibrary.spi.tasks.TrackingPlatformTaskAdapter
import java.time.Duration
import kotlin.math.ceil

private const val NANOS_PER_TICK = 50_000_000.0

/** Rounds a non-zero duration up to the next server tick. */
internal fun durationToTicks(value: Duration): Long =
    if (value.isZero) 0 else ceil(value.toNanos() / NANOS_PER_TICK).toLong().coerceAtLeast(1)

internal class BukkitTaskAdapter(
    private val plugin: Plugin,
) : TrackingPlatformTaskAdapter() {
    private val foliaScheduler by lazy { FoliaSchedulerBridge(plugin) }

    override fun scheduleNative(request: PlatformTaskRequest): PlatformTaskHandle {
        val native = if (ServerCapabilities.isFolia) foliaScheduler.schedule(request) else scheduleBukkit(request)
        return NativeHandle(native)
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

    private inner class NativeHandle(
        private val native: Any,
    ) : PlatformTaskHandle {
        override fun cancel(): Boolean {
            when (native) {
                is BukkitTask -> native.cancel()
                else -> foliaScheduler.cancel(native)
            }
            return true
        }
    }
}
