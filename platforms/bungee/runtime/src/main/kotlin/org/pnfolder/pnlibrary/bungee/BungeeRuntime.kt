package org.pnfolder.pnlibrary.bungee

import net.md_5.bungee.api.ProxyServer
import net.md_5.bungee.api.plugin.Plugin
import org.pnfolder.pnlibrary.api.logging.PnLogger
import org.pnfolder.pnlibrary.bungee.api.BungeePlatform
import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
import org.pnfolder.pnlibrary.core.exceptions.ResourceCleanup
import org.pnfolder.pnlibrary.core.link.LinkReceiver
import org.pnfolder.pnlibrary.core.runtime.PnLibraryRuntime
import org.pnfolder.pnlibrary.core.runtime.PnLibraryRuntimeHost
import org.pnfolder.pnlibrary.core.runtime.ProxyRuntime

/**
 * Owns the shared pnLibrary runtime for the BungeeCord plugin.
 *
 * [start] binds the Bungee platform and marks the adapter ready once the runtime is up; a failure
 * rolls back inside [PnLibraryRuntimeHost.launchPlatform]. [stop] closes the host and is safe to
 * call more than once.
 */
internal class BungeeRuntime(private val plugin: Plugin) {
    private var proxyRuntime: ProxyRuntime? = null
    private var host: PnLibraryRuntimeHost? = null
    private var playerEvents: BungeePlayerEvents? = null

    /** Opens the runtime, player events, game-server link and administrator reminders; undoes everything on failure. */
    fun start() {
        try {
            open()
        } catch (error: Throwable) {
            runCatchingNonFatal(::stop).onFailure(error::addSuppressed)
            throw error
        }
    }

    private fun open() {
        var metricsLogger: PnLogger? = null
        val adapter = BungeePlatformAdapter(plugin) { metricsLogger!! }
        val started = PnLibraryRuntime.builder()
            .owner(plugin)
            .adapter(adapter)
            .platform<BungeePlatform>(BungeePlatformServices(plugin.proxy))
            .launch(adapter::markReady)
        host = started
        metricsLogger = started.library.logging.logger(plugin, "Metrics")

        val proxy = ProxyRuntime(adapter, ::registerLinkListener, adapter.networkProxy::describePlugin)
        proxyRuntime = proxy
        proxy.start(started)

        playerEvents = BungeePlayerEvents(started.library)
            .also { plugin.proxy.pluginManager.registerListener(plugin, it) }
    }

    private fun registerLinkListener(receiver: LinkReceiver): AutoCloseable {
        val listener = BungeeLinkListener(receiver)
        plugin.proxy.pluginManager.registerListener(plugin, listener)
        return AutoCloseable { plugin.proxy.pluginManager.unregisterListener(listener) }
    }

    /** Cancels tasks, unregisters listeners and the link channel, then closes the host. Safe to call again. */
    fun stop() {
        val proxy = proxyRuntime
        val events = playerEvents
        val started = host
        proxyRuntime = null
        playerEvents = null
        host = null

        ResourceCleanup.closeAll(
            { proxy?.close() },
            { events?.let { plugin.proxy.pluginManager.unregisterListener(it) } },
            { started?.close() },
        )
    }
}

/** Native-backed Bungee platform API installed by the Bungee runtime. */
private class BungeePlatformServices(
    override val proxy: ProxyServer,
) : BungeePlatform
