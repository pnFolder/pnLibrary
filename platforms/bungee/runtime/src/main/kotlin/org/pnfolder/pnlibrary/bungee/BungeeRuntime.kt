package org.pnfolder.pnlibrary.bungee

import net.md_5.bungee.api.plugin.Plugin
import net.md_5.bungee.api.scheduler.ScheduledTask
import org.pnfolder.pnlibrary.bungee.api.BungeePlatform
import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
import org.pnfolder.pnlibrary.core.link.LinkCodec
import org.pnfolder.pnlibrary.core.menu.OwnerInfo
import org.pnfolder.pnlibrary.core.proxy.ProxyLink
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
    private var link: ProxyLink? = null
    private var networkTask: ScheduledTask? = null

    fun start() {
        try {
            open()
        } catch (error: Throwable) {
            // Whatever opened before the failure must not stay registered on a proxy that goes on without us.
            runCatchingNonFatal(::stop)
            throw error
        }
    }

    private fun open() {
        val adapter = BungeePlatformAdapter(plugin)
        val started = PnLibraryRuntimeHost.launchPlatform(
            plugin,
            adapter,
            BungeePlatform::class.java,
            BungeePlatformServices(plugin.proxy),
            adapter::markReady,
        )
        host = started
        val listener = BungeePlayerEvents(started.library)
        plugin.proxy.pluginManager.registerListener(plugin, listener)
        playerEvents = listener
        connectLink(started)
    }

    /** Opens the link to the game servers and, when it is secured, starts announcing this proxy. */
    private fun connectLink(started: PnLibraryRuntimeHost) {
        val proxyLink = ProxyLink(
            library = started.library,
            folder = plugin.dataFolder.toPath(),
            warn = plugin.logger::warning,
            transport = { name, bytes ->
                val server = plugin.proxy.servers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
                server?.sendData(LinkCodec.CHANNEL, bytes, false) ?: false
            },
            describe = { owner -> (owner as? Plugin)?.description?.let { OwnerInfo(it.name, it.version, listOfNotNull(it.author)) } },
            platformName = { "${plugin.proxy.name} ${plugin.proxy.version}" },
            players = { plugin.proxy.onlineCount },
            broadcast = { message -> plugin.proxy.servers.values.forEach { runCatchingNonFatal { it.sendData(LinkCodec.CHANNEL, message, false) } } },
        )
        link = proxyLink
        val listener = BungeeLinkListener(proxyLink.receiver)
        plugin.proxy.registerChannel(LinkCodec.CHANNEL)
        plugin.proxy.pluginManager.registerListener(plugin, listener)
        linkListener = listener
        if (proxyLink.announces) {
            networkTask = plugin.proxy.scheduler.schedule(
                plugin,
                proxyLink::announce,
                ProxyLink.NETWORK_DELAY_SECONDS,
                ProxyLink.NETWORK_PERIOD_SECONDS,
                TimeUnit.SECONDS,
            )
        }
    }

    fun stop() {
        networkTask?.cancel()
        networkTask = null
        linkListener?.let { runCatchingNonFatal { plugin.proxy.pluginManager.unregisterListener(it) } }
        linkListener = null
        link?.let { runCatchingNonFatal(it::close) }
        link = null
        runCatchingNonFatal { plugin.proxy.unregisterChannel(LinkCodec.CHANNEL) }
        playerEvents?.let { runCatchingNonFatal { plugin.proxy.pluginManager.unregisterListener(it) } }
        playerEvents = null
        host?.close()
        host = null
    }
}
