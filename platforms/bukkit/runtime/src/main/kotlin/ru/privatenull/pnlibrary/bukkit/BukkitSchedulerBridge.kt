package ru.privatenull.pnlibrary.bukkit

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import ru.privatenull.pnlibrary.api.logging.LogLevel
import ru.privatenull.pnlibrary.bukkit.compat.ServerCapabilities

/** Dispatches work across Bukkit and Folia without exposing reflective details to the adapter. */
internal class BukkitSchedulerBridge(
    private val plugin: Plugin,
    private val errorLogger: (Any, LogLevel, String, Throwable?) -> Unit,
) {
    fun executeGlobal(task: Runnable) {
        if (ServerCapabilities.isFolia) {
            try {
                val scheduler = Bukkit::class.java.getMethod("getGlobalRegionScheduler").invoke(null)
                val run = scheduler.javaClass.getMethod(
                    "run", Plugin::class.java, java.util.function.Consumer::class.java,
                )
                run.invoke(scheduler, plugin, java.util.function.Consumer<Any> { task.run() })
            } catch (error: ReflectiveOperationException) {
                errorLogger(plugin, LogLevel.ERROR, "Не удалось передать задачу Folia GlobalRegionScheduler", error)
            }
            return
        }

        if (Bukkit.isPrimaryThread()) task.run()
        else Bukkit.getScheduler().runTask(plugin, task)
    }

    fun executeEntity(player: Player, task: Runnable) {
        try {
            val scheduler = player.javaClass.getMethod("getScheduler").invoke(player)
            val run = scheduler.javaClass.getMethod(
                "run", Plugin::class.java, java.util.function.Consumer::class.java, Runnable::class.java,
            )
            run.invoke(scheduler, plugin, java.util.function.Consumer<Any> { task.run() }, null)
        } catch (error: Exception) {
            errorLogger(plugin, LogLevel.ERROR, "Не удалось передать задачу Folia EntityScheduler", error)
        }
    }
}
