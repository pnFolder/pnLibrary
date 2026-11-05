package org.pnfolder.pnlibrary.bukkit.tasks

import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.pnfolder.pnlibrary.api.tasks.TaskExecution
import org.pnfolder.pnlibrary.bukkit.commands.BukkitCommandSender
import org.pnfolder.pnlibrary.bukkit.compat.Folia
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskRequest

/** Puts a portable task on the Folia scheduler that matches its execution: asynchronous, a player's region or global. */
internal class FoliaSchedulerBridge(private val plugin: Plugin) {

    fun schedule(request: PlatformTaskRequest): Any {
        val task = request.callback
        val interval = request.interval
        val native = when (request.executionKind) {
            TaskExecution.Kind.ASYNC -> when {
                interval != null -> Folia.runAsyncRepeating(plugin, request.delay, interval, task)
                request.delay.isZero -> Folia.runAsync(plugin, task)
                else -> Folia.runAsyncLater(plugin, request.delay, task)
            }
            TaskExecution.Kind.ENTITY -> scheduleOnPlayer(player(request.target), request)
            TaskExecution.Kind.GLOBAL -> when {
                interval != null -> Folia.runGlobalRepeating(plugin, durationToTicks(request.delay), durationToTicks(interval), task)
                request.delay.isZero -> Folia.runGlobal(plugin, task)
                else -> Folia.runGlobalLater(plugin, durationToTicks(request.delay), task)
            }
        }
        return checkNotNull(native) { "Folia did not schedule the task: the player has already left" }
    }

    fun cancel(task: Any) = Folia.cancel(task)

    // Entity schedulers count from one tick: a delay of zero is not accepted for a delayed or repeating task.
    private fun scheduleOnPlayer(player: Player, request: PlatformTaskRequest): Any? {
        val delay = durationToTicks(request.delay).coerceAtLeast(1)
        val interval = request.interval
        return when {
            interval != null -> Folia.runOnRepeating(player, plugin, delay, durationToTicks(interval), request.callback)
            request.delay.isZero -> Folia.runOn(player, plugin, request.callback)
            else -> Folia.runOnLater(player, plugin, delay, request.callback)
        }
    }

    private fun player(target: Any?): Player {
        val native = (target as? BukkitCommandSender)?.native ?: target
        return requireNotNull(native as? Player) { "A Folia entity task needs a Bukkit player as its target" }
    }
}
