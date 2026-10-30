package org.pnfolder.pnlibrary.bungee

import net.md_5.bungee.api.plugin.Plugin
import net.md_5.bungee.api.scheduler.ScheduledTask
import org.pnfolder.pnlibrary.bungee.api.BungeePlatform
import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
import org.pnfolder.pnlibrary.core.link.LinkCodec
import org.pnfolder.pnlibrary.core.link.LinkHub
import org.pnfolder.pnlibrary.core.link.LinkReceiver
import org.pnfolder.pnlibrary.core.link.LinkSecret
import org.pnfolder.pnlibrary.core.link.LinkTransport
import org.pnfolder.pnlibrary.core.link.NetworkPublisher
import org.pnfolder.pnlibrary.core.link.RemoteExecutor
import org.pnfolder.pnlibrary.core.runtime.PnLibraryRuntimeHost
import java.util.concurrent.TimeUnit

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
    private var networkTask: ScheduledTask? = null

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
        val key = LinkSecret.readOrCreate(plugin.dataFolder.toPath())
        val transport = LinkTransport { name, bytes -> plugin.proxy.getServerInfo(name)?.sendData(LinkCodec.CHANNEL, bytes, false) ?: false }
        LinkHub.key = key
        LinkHub.transport = transport
        val link = BungeeLinkListener(LinkReceiver(key, executor = RemoteExecutor(started.library), transport = transport))
        plugin.proxy.registerChannel(LinkCodec.CHANNEL)
        plugin.proxy.pluginManager.registerListener(plugin, link)
        linkListener = link
        if (key != null) {
            val publisher = NetworkPublisher(started.library, key, { "${plugin.proxy.name} ${plugin.proxy.version}" }, { plugin.proxy.onlineCount })
            networkTask = plugin.proxy.scheduler.schedule(plugin, {
                val message = publisher.message()
                plugin.proxy.servers.values.forEach { runCatchingNonFatal { it.sendData(LinkCodec.CHANNEL, message, false) } }
            }, NETWORK_DELAY_SECONDS, NETWORK_PERIOD_SECONDS, TimeUnit.SECONDS)
        }
    }

    private companion object {
        const val NETWORK_DELAY_SECONDS = 10L
        const val NETWORK_PERIOD_SECONDS = 30L
    }

    fun stop() {
        networkTask?.cancel()
        LinkHub.transport = null
        LinkHub.key = null
        networkTask = null
        linkListener?.let { runCatchingNonFatal { plugin.proxy.pluginManager.unregisterListener(it) } }
        linkListener = null
        runCatchingNonFatal { plugin.proxy.unregisterChannel(LinkCodec.CHANNEL) }
        playerEvents?.let { runCatchingNonFatal { plugin.proxy.pluginManager.unregisterListener(it) } }
        playerEvents = null
        host?.close()
        host = null
    }
}
