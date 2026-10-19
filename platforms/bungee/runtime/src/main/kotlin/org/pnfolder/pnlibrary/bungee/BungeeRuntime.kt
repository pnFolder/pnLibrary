package org.pnfolder.pnlibrary.bungee

import net.md_5.bungee.api.plugin.Plugin
import org.pnfolder.pnlibrary.bungee.api.BungeePlatform
import org.pnfolder.pnlibrary.core.runtime.PnLibraryRuntimeHost

/**
 * Owns the shared pnLibrary runtime for the BungeeCord plugin.
 *
 * [start] binds the Bungee platform and marks the adapter ready once the runtime is up; a failure
 * rolls back inside [PnLibraryRuntimeHost.launchPlatform]. [stop] closes the host and is safe to
 * call more than once.
 */
internal class BungeeRuntime(private val plugin: Plugin) {
    private var host: PnLibraryRuntimeHost? = null

    fun start() {
        val adapter = BungeePlatformAdapter(plugin)
        host = PnLibraryRuntimeHost.launchPlatform(
            plugin,
            adapter,
            BungeePlatform::class.java,
            BungeePlatformImpl(plugin.proxy),
            adapter::markReady,
        )
    }

    fun stop() {
        host?.close()
        host = null
    }
}
