package org.pnfolder.pnlibrary.bukkit.inventory

import org.bukkit.Bukkit
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.pnfolder.pnlibrary.bukkit.compat.ServerCapabilities
import java.util.function.Consumer

/** Native inventory access must run on the player's owning thread, including bulk cleanup. */
internal interface MenuExecution {
    fun requireOwner(player: Player)
    fun execute(player: Player, action: () -> Unit)
}

/**
 * Runs menu actions on the thread that owns the player: immediately when already there, otherwise
 * scheduled on the player's region (Folia) or the main thread.
 */
internal class NativeMenuExecution(private val plugin: Plugin) : MenuExecution {
    private fun owns(player: Player): Boolean = if (ServerCapabilities.isFolia) {
        Bukkit::class.java.getMethod("isOwnedByCurrentRegion", Entity::class.java).invoke(null, player) as Boolean
    } else Bukkit.isPrimaryThread()

    override fun requireOwner(player: Player) {
        check(owns(player)) { "Menu access requires the player's owning thread; schedule an entity task first" }
    }

    override fun execute(player: Player, action: () -> Unit) {
        if (owns(player)) { action(); return }
        if (!plugin.isEnabled) {
            plugin.logger.warning("Menu tracking was cleared during shutdown; native views require server/player cleanup")
            return
        }
        if (ServerCapabilities.isFolia) {
            val scheduler = player.javaClass.getMethod("getScheduler").invoke(player)
            val type = Class.forName("io.papermc.paper.threadedregions.scheduler.EntityScheduler")
            type.getMethod("run", Plugin::class.java, Consumer::class.java, Runnable::class.java)
                .invoke(scheduler, plugin, Consumer<Any> { action() }, Runnable { })
        } else Bukkit.getScheduler().runTask(plugin, Runnable(action))
    }
}
