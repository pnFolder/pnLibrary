package ru.privatenull.pnlibrary.bukkit.tasks

import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitScheduler
import org.bukkit.scheduler.BukkitTask
import ru.privatenull.pnlibrary.api.tasks.TaskExecution
import ru.privatenull.pnlibrary.bukkit.compat.ServerCapabilities
import ru.privatenull.pnlibrary.spi.tasks.PlatformTaskHandle
import ru.privatenull.pnlibrary.spi.tasks.PlatformTaskRequest
import ru.privatenull.pnlibrary.spi.tasks.TrackingPlatformTaskAdapter
import java.time.Duration
import kotlin.math.ceil

private const val NANOS_PER_TICK = 50_000_000.0

/** Rounds a non-zero duration up to the next server tick. */
internal fun durationToTicks(value: Duration): Long =
    if (value.isZero) 0 else ceil(value.toNanos() / NANOS_PER_TICK).toLong().coerceAtLeast(1)

/**
 * Owns scheduled tasks and adapts requests to Bukkit or Folia.
 *
 * Ordinary Bukkit executes entity and global tasks on the primary thread. Folia dispatches
 * entity tasks to their player scheduler and global tasks to the global-region scheduler.
 */
internal class BukkitTaskAdapter(
    private val plugin: Plugin,
) : TrackingPlatformTaskAdapter() {
    private val foliaScheduler by lazy { FoliaSchedulerBridge(plugin) }

    override fun scheduleNative(request: PlatformTaskRequest): PlatformTaskHandle {
        val native = if (ServerCapabilities.isFolia) foliaScheduler.schedule(request) else scheduleBukkit(request)
        return NativeHandle(native)
    }

    /** Routes ordinary Bukkit requests to asynchronous or primary-thread scheduling. */
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

    /** Schedules asynchronous one-shot or repeating work. */
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

    /** Schedules primary-thread one-shot or repeating work. */
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
