package org.pnfolder.pnlibrary.bungee

import net.md_5.bungee.api.plugin.Plugin
import org.pnfolder.pnlibrary.bungee.api.BungeePlatform
import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
import org.pnfolder.pnlibrary.core.link.LinkCodec
import org.pnfolder.pnlibrary.core.link.LinkReceiver
import org.pnfolder.pnlibrary.core.link.LinkSecret
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
    private var linkListener: BungeeLinkListener? = null

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
        val link = BungeeLinkListener(LinkReceiver(LinkSecret.readOrCreate(plugin.dataFolder.toPath())))
        plugin.proxy.registerChannel(LinkCodec.CHANNEL)
        plugin.proxy.pluginManager.registerListener(plugin, link)
        linkListener = link
    }

    fun stop() {
        linkListener?.let { runCatchingNonFatal { plugin.proxy.pluginManager.unregisterListener(it) } }
        linkListener = null
        runCatchingNonFatal { plugin.proxy.unregisterChannel(LinkCodec.CHANNEL) }
        playerEvents?.let { runCatchingNonFatal { plugin.proxy.pluginManager.unregisterListener(it) } }
        playerEvents = null
        host?.close()
        host = null
    }
}
