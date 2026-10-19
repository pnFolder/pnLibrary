package org.pnfolder.pnlibrary.bungee

import net.md_5.bungee.api.plugin.Plugin

/** BungeeCord entry point for pnLibrary. All startup and shutdown work lives in [BungeeRuntime]. */
class PnLibraryBungeePlugin : Plugin() {
    private val runtime = BungeeRuntime(this)

    override fun onEnable() = runtime.start()

    override fun onDisable() = runtime.stop()
}
