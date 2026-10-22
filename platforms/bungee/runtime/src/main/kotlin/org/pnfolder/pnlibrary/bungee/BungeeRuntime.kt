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
    private var playerEvents: BungeePlayerEvents? = null

    fun start() {
        val adapter = BungeePlatformAdapter(plugin)
        val started = PnLibraryRuntimeHost.launchPlatform(
            plugin,
            adapter,
            BungeePlatform::class.java,
            BungeePlatformImpl(plugin.proxy),
            adapter::markReady,
        )
        host = started
        val listener = BungeePlayerEvents(started.library)
        plugin.proxy.pluginManager.registerListener(plugin, listener)
        playerEvents = listener
    }

    fun stop() {
        playerEvents?.let { runCatching { plugin.proxy.pluginManager.unregisterListener(it) } }
        playerEvents = null
        host?.close()
        host = null
    }
}
