package org.pnfolder.pnlibrary.bungee

import net.md_5.bungee.api.plugin.Plugin
import net.md_5.bungee.api.scheduler.ScheduledTask
import org.pnfolder.pnlibrary.bungee.api.BungeePlatform
import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
import org.pnfolder.pnlibrary.core.link.LinkCodec
import org.pnfolder.pnlibrary.core.menu.OwnerInfo
import org.pnfolder.pnlibrary.core.proxy.ProxyLink
import org.pnfolder.pnlibrary.core.runtime.PnLibraryRuntimeHost
import org.pnfolder.pnlibrary.core.updates.catalog.ProxyUpdateReminder
import java.util.concurrent.TimeUnit
import java.util.logging.Level

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
    private var reminderTask: ScheduledTask? = null

    /** Opens the runtime, player events, game-server link and administrator reminders; undoes everything on failure. */
    fun start() {
        try {
            open()
        } catch (error: Throwable) {
            // Whatever opened before the failure must not stay registered on a proxy that goes on without us.
            runCatchingNonFatal(::stop).onFailure(error::addSuppressed)
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
        remindAdministrators(started)
    }

    /** Repeats the update notice in chat to administrators; the interval is fixed in code and cannot be turned off. */
    private fun remindAdministrators(started: PnLibraryRuntimeHost) {
        reminderTask = plugin.proxy.scheduler.schedule(
            plugin,
            {
                plugin.proxy.players.filter { ProxyUpdateReminder.canSee(it::hasPermission) }.forEach { ProxyUpdateReminder.send(started.library, it.uniqueId) }
            },
            ProxyUpdateReminder.INTERVAL_SECONDS,
            ProxyUpdateReminder.INTERVAL_SECONDS,
            TimeUnit.SECONDS,
        )
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
            holds = { viewer, permission -> plugin.proxy.getPlayer(viewer)?.hasPermission(permission) == true },
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

    /** Cancels tasks, unregisters listeners and the link channel, then closes the host. Safe to call again. */
    fun stop() {
        networkTask?.cancel()
        reminderTask?.cancel()
        reminderTask = null
        networkTask = null
        linkListener?.let { release("link listener") { plugin.proxy.pluginManager.unregisterListener(it) } }
        linkListener = null
        link?.let { release("link", it::close) }
        link = null
        release("link channel") { plugin.proxy.unregisterChannel(LinkCodec.CHANNEL) }
        playerEvents?.let { release("player events") { plugin.proxy.pluginManager.unregisterListener(it) } }
        playerEvents = null
        host?.close()
        host = null
    }

    /** One part failing to stop must not keep the others from stopping, but it must not vanish either. */
    private fun release(part: String, action: () -> Unit) {
        runCatchingNonFatal(action).onFailure { plugin.logger.log(Level.SEVERE, "Could not stop $part", it) }
    }
}
