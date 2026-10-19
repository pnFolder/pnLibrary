package org.pnfolder.pnlibrary.bukkit

import org.bukkit.plugin.java.JavaPlugin

/** Bukkit entry point for pnLibrary. All startup and shutdown work lives in [BukkitRuntime]. */
class PnLibraryBukkitPlugin : JavaPlugin() {
    private val runtime = BukkitRuntime(this)

    override fun onEnable() = runtime.start()

    override fun onDisable() = runtime.stop()
}
