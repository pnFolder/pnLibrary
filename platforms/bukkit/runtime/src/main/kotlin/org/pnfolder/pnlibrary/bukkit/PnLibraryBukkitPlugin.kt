package org.pnfolder.pnlibrary.bukkit

import org.bukkit.plugin.java.JavaPlugin

/** Bukkit entry point for pnLibrary. All startup and shutdown work lives in [BukkitRuntime]. */
class PnLibraryBukkitPlugin : JavaPlugin() {
    private val runtime = BukkitRuntime(this)

    /** Starts pnLibrary when the server enables the plugin. */
    override fun onEnable() = runtime.start()

    /** Stops pnLibrary when the server disables the plugin. */
    override fun onDisable() = runtime.stop()
}
