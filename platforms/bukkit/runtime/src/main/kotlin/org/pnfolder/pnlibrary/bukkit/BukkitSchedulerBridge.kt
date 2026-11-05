package org.pnfolder.pnlibrary.bukkit

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.pnfolder.pnlibrary.api.logging.LogLevel
import org.pnfolder.pnlibrary.bukkit.compat.Folia
import org.pnfolder.pnlibrary.bukkit.compat.ServerCapabilities
import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal

/** Runs work on the main thread, or on Folia on the global region or the region of a player. */
internal class BukkitSchedulerBridge(
    private val plugin: Plugin,
    private val errorLogger: (Any, LogLevel, String, Throwable?) -> Unit,
) {
    fun executeGlobal(task: Runnable) {
        if (ServerCapabilities.isFolia) {
            runCatchingNonFatal { Folia.runGlobal(plugin, task) }
                .onFailure { errorLogger(plugin, LogLevel.ERROR, "Не удалось передать задачу Folia GlobalRegionScheduler", it) }
            return
        }
        if (Bukkit.isPrimaryThread()) task.run() else Bukkit.getScheduler().runTask(plugin, task)
    }

    /** Folia only: runs [task] on the region of [player]. */
    fun executeEntity(player: Player, task: Runnable) {
        runCatchingNonFatal { Folia.runOn(player, plugin, task) }
            .onFailure { errorLogger(plugin, LogLevel.ERROR, "Не удалось передать задачу Folia EntityScheduler", it) }
    }
}
