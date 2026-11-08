package org.pnfolder.pnlibrary.bukkit.inventory

import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.pnfolder.pnlibrary.bukkit.tasks.ServerScheduler

/** Native inventory access must run on the player's owning thread, including bulk cleanup. */
internal interface MenuExecution {
    /** Fails when the current thread may not touch [player]'s inventory. */
    fun requireOwner(player: Player)
    /** Runs [action] on the thread that owns [player]. */
    fun execute(player: Player, action: () -> Unit)
}

/**
 * Runs menu actions on the thread that owns the player: immediately when already there, otherwise
 * scheduled on the player's region (Folia) or the main thread.
 */
internal class NativeMenuExecution(private val plugin: Plugin) : MenuExecution {
    private val scheduler = ServerScheduler(plugin)

    override fun requireOwner(player: Player) {
        check(scheduler.owns(player)) { "Menu access requires the player's owning thread; schedule an entity task first" }
    }

    override fun execute(player: Player, action: () -> Unit) {
        if (scheduler.owns(player)) {
            action()
            return
        }
        // A disabled plugin may not schedule anything; the server closes the views itself.
        if (!plugin.isEnabled) {
            plugin.logger.warning("Menu tracking was cleared during shutdown; native views require server/player cleanup")
            return
        }
        scheduler.onPlayer(player, Runnable(action))
    }
}
