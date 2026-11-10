package org.pnfolder.pnlibrary.bukkit.tasks

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.pnfolder.pnlibrary.bukkit.compat.Folia
import org.pnfolder.pnlibrary.bukkit.compat.ServerCapabilities

/**
 * Runs pnLibrary's own work on the thread that may do it.
 *
 * On Bukkit and Paper that is the main thread. Folia has no main thread and its Bukkit scheduler throws on every call,
 * so there global work goes to the global region and work that touches a player goes to that player's region.
 * Delays are in server ticks.
 */
internal class ServerScheduler(private val plugin: Plugin) {
    /** Work that is waiting and can still be withdrawn. */
    fun interface Scheduled {
        /** Withdraws the work if it has not run yet. */
        fun cancel()
    }

    /** Runs [task] at once when this thread may touch the world, otherwise on the next tick. */
    fun execute(task: Runnable) {
        if (!ServerCapabilities.isFolia && Bukkit.isPrimaryThread()) task.run() else nextTick(task)
    }

    /** Runs [task] on the next tick, even when called from the main thread. */
    fun nextTick(task: Runnable) {
        if (ServerCapabilities.isFolia) Folia.runGlobal(plugin, task) else Bukkit.getScheduler().runTask(plugin, task)
    }

    /** Runs [task] once after [delayTicks]. Folia refuses a delay of zero ticks, so it is raised to one. */
    fun later(delayTicks: Long, task: Runnable): Scheduled {
        if (!ServerCapabilities.isFolia) return Scheduled(Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks)::cancel)
        return foliaTask(Folia.runGlobalLater(plugin, delayTicks.coerceAtLeast(1), task))
    }

    /** Runs [task] every [periodTicks] after [delayTicks]; on Folia both are raised to at least one tick. */
    fun repeat(delayTicks: Long, periodTicks: Long, task: Runnable): Scheduled {
        if (!ServerCapabilities.isFolia) return Scheduled(Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks)::cancel)
        return foliaTask(Folia.runGlobalRepeating(plugin, delayTicks.coerceAtLeast(1), periodTicks.coerceAtLeast(1), task))
    }

    /** Runs [task] off the server threads. */
    fun async(task: Runnable) {
        if (ServerCapabilities.isFolia) Folia.runAsync(plugin, task) else Bukkit.getScheduler().runTaskAsynchronously(plugin, task)
    }

    /** Whether this thread may touch [player] now. */
    fun owns(player: Player): Boolean =
        if (ServerCapabilities.isFolia) Folia.ownsRegionOf(player) else Bukkit.isPrimaryThread()

    /** Runs [task] at once when this thread owns [player], otherwise on the player's thread. On Folia nothing runs once the player has left. */
    fun onPlayer(player: Player, task: Runnable) {
        when {
            owns(player) -> task.run()
            ServerCapabilities.isFolia -> Folia.runOn(player, plugin, task)
            else -> Bukkit.getScheduler().runTask(plugin, task)
        }
    }

    /** Runs [task] on the thread of [player] after [delayTicks]. */
    fun onPlayerLater(player: Player, delayTicks: Long, task: Runnable) {
        if (ServerCapabilities.isFolia) Folia.runOnLater(player, plugin, delayTicks.coerceAtLeast(1), task)
        else Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks)
    }

    private fun foliaTask(native: Any?) = Scheduled { native?.let(Folia::cancel) }
}
