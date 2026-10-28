package org.pnfolder.pnlibrary.bukkit

import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.server.PluginEnableEvent
import org.bukkit.plugin.Plugin

/**
 * Keeps plugins that depend on a switched-off pnLibrary from running.
 *
 * Bukkit enables them whether or not their dependency started, so each is disabled as it enables. pnLibrary stays
 * enabled, inert, until [later] runs after the server has started, then switches itself off.
 */
internal class BukkitDependentGuard(
    private val plugin: Plugin,
    private val later: (Runnable) -> Unit,
    private val log: (String) -> Unit,
) : Listener {

    /** Disables the dependents already running, then watches for those that enable later. */
    fun arm() {
        disableDependentsOf(plugin, log)
        Bukkit.getPluginManager().registerEvents(this, plugin)
        later(Runnable { Bukkit.getPluginManager().disablePlugin(plugin) })
    }

    @EventHandler
    fun onEnable(event: PluginEnableEvent) {
        if (dependsOn(event.plugin, plugin)) disable(event.plugin, plugin, log)
    }

    companion object {
        /** Disables every enabled plugin that depends on [owner]. */
        fun disableDependentsOf(owner: Plugin, log: (String) -> Unit) {
            Bukkit.getPluginManager().plugins
                .filter { it !== owner && it.isEnabled && dependsOn(it, owner) }
                .forEach { disable(it, owner, log) }
        }

        private fun dependsOn(candidate: Plugin, owner: Plugin): Boolean {
            val description = candidate.description
            return candidate !== owner && (owner.name in description.depend || owner.name in description.softDepend)
        }

        private fun disable(candidate: Plugin, owner: Plugin, log: (String) -> Unit) {
            log("${candidate.name} отключён: ${owner.name} не запущен")
            Bukkit.getPluginManager().disablePlugin(candidate)
        }
    }
}
