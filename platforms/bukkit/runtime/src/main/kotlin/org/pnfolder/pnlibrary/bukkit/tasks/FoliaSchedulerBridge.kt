package org.pnfolder.pnlibrary.bukkit.tasks

import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.pnfolder.pnlibrary.api.tasks.TaskExecution
import org.pnfolder.pnlibrary.bukkit.commands.BukkitCommandSender
import org.pnfolder.pnlibrary.bukkit.compat.Folia
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskRequest
import org.pnfolder.pnlibrary.spi.tasks.RetirableCallback
import java.time.Duration

/**
 * Puts a portable task on the Folia scheduler that matches its execution: asynchronous, a player's region or global.
 *
 * Region schedulers count from one tick: they refuse a delay or period of zero for delayed and repeating work, so
 * both are raised to one tick there.
 */
internal class FoliaSchedulerBridge(private val plugin: Plugin) {

    /** Schedules [request] on the matching Folia scheduler; the returned handle goes to [cancel]. */
    fun schedule(request: PlatformTaskRequest): Any {
        val native = when (request.executionKind) {
            TaskExecution.Kind.ASYNC -> scheduleAsync(request)
            TaskExecution.Kind.ENTITY -> scheduleOnPlayer(player(request.target), request)
            TaskExecution.Kind.GLOBAL -> scheduleGlobal(request)
        }
        return checkNotNull(native) { "Folia did not schedule the task: the player has already left" }
    }

    /** Withdraws a task returned by [schedule]. */
    fun cancel(task: Any) = Folia.cancel(task)

    private fun scheduleAsync(request: PlatformTaskRequest): Any? {
        val interval = request.interval
        return when {
            interval != null -> Folia.runAsyncRepeating(plugin, request.delay, interval, request.callback)
            request.delay.isZero -> Folia.runAsync(plugin, request.callback)
            else -> Folia.runAsyncLater(plugin, request.delay, request.callback)
        }
    }

    private fun scheduleGlobal(request: PlatformTaskRequest): Any? {
        val interval = request.interval
        return when {
            interval != null -> Folia.runGlobalRepeating(plugin, ticks(request.delay), ticks(interval), request.callback)
            request.delay.isZero -> Folia.runGlobal(plugin, request.callback)
            else -> Folia.runGlobalLater(plugin, ticks(request.delay), request.callback)
        }
    }

    private fun scheduleOnPlayer(player: Player, request: PlatformTaskRequest): Any? {
        val interval = request.interval
        val retired = Runnable { (request.callback as? RetirableCallback)?.retire() }
        return when {
            interval != null -> Folia.runOnRepeating(player, plugin, ticks(request.delay), ticks(interval), request.callback, retired)
            request.delay.isZero -> Folia.runOn(player, plugin, request.callback, retired)
            else -> Folia.runOnLater(player, plugin, ticks(request.delay), request.callback, retired)
        }
    }

    private fun ticks(value: Duration): Long = durationToTicks(value).coerceAtLeast(1)

    private fun player(target: Any?): Player {
        val native = (target as? BukkitCommandSender)?.native ?: target
        return requireNotNull(native as? Player) { "A Folia entity task needs a Bukkit player as its target" }
    }
}
