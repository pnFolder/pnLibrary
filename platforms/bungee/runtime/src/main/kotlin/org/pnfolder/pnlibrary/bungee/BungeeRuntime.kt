package org.pnfolder.pnlibrary.bungee

import net.md_5.bungee.api.plugin.Plugin
import org.pnfolder.pnlibrary.api.logging.PnLogger
import org.pnfolder.pnlibrary.bungee.api.BungeePlatform
import org.pnfolder.pnlibrary.bungee.link.BungeeLinkListener
import org.pnfolder.pnlibrary.bungee.network.BungeePlayerEvents
import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
import org.pnfolder.pnlibrary.core.exceptions.ResourceCleanup
import org.pnfolder.pnlibrary.core.link.LinkReceiver
import org.pnfolder.pnlibrary.core.runtime.PnLibraryRuntime
import org.pnfolder.pnlibrary.core.runtime.PnLibraryRuntimeHost
import org.pnfolder.pnlibrary.core.runtime.ProxyRuntime

internal class BungeeRuntime(private val plugin: Plugin) {
    private var proxyRuntime: ProxyRuntime? = null
    private var runtimeHost: PnLibraryRuntimeHost? = null
    private var playerEvents: BungeePlayerEvents? = null

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
        val host = PnLibraryRuntime.builder()
            .owner(plugin)
            .adapter(adapter)
            .platform<BungeePlatform>(object : BungeePlatform { override val proxy = plugin.proxy })
            .launch(adapter::markReady)
        runtimeHost = host
        metricsLogger = host.library.logging.logger(plugin, "Metrics")

        val proxy = ProxyRuntime(adapter, ::registerLinkListener, adapter.networkProxy::describePlugin)
        proxyRuntime = proxy
        proxy.start(host)

        playerEvents = BungeePlayerEvents(host.library)
            .also { plugin.proxy.pluginManager.registerListener(plugin, it) }
    }

    private fun registerLinkListener(receiver: LinkReceiver): AutoCloseable {
        val listener = BungeeLinkListener(receiver)
        plugin.proxy.pluginManager.registerListener(plugin, listener)
        return AutoCloseable { plugin.proxy.pluginManager.unregisterListener(listener) }
    }

    fun stop() {
        val proxy = proxyRuntime
        val events = playerEvents
        val host = runtimeHost
        proxyRuntime = null
        playerEvents = null
        runtimeHost = null

        ResourceCleanup.closeAll(
            { proxy?.close() },
            { events?.let { plugin.proxy.pluginManager.unregisterListener(it) } },
            { host?.close() },
        )
    }
}
