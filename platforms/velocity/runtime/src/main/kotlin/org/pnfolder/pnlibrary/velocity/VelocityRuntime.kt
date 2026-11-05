package org.pnfolder.pnlibrary.velocity

import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.scheduler.ScheduledTask
import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
import org.pnfolder.pnlibrary.core.commands.proxy.ProxyControlCommand
import org.pnfolder.pnlibrary.core.link.Link
import org.pnfolder.pnlibrary.core.link.LinkReceiver
import org.pnfolder.pnlibrary.core.link.LinkSecret
import org.pnfolder.pnlibrary.core.link.LinkTransport
import org.pnfolder.pnlibrary.core.link.NetworkPublisher
import org.pnfolder.pnlibrary.core.link.RemoteExecutor
import org.pnfolder.pnlibrary.core.menu.OwnerInfo
import org.pnfolder.pnlibrary.core.menu.ProxyMenu
import org.pnfolder.pnlibrary.core.runtime.PnLibraryRuntimeHost
import org.pnfolder.pnlibrary.velocity.api.VelocityPlatform
import org.slf4j.Logger
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * Velocity runtime behind the generated `PnLibraryVelocityPlugin` descriptor class.
 *
 * The descriptor carries Velocity's `@Plugin` metadata and forwards lifecycle events here. [owner] is
 * the registered Velocity plugin instance, which pnLibrary uses for identity and diagnostics. The
 * initialize event creates one [PnLibraryRuntimeHost]; the shutdown event closes it.
 */
internal class VelocityRuntime(
    private val owner: Any,
    private val server: ProxyServer,
    private val logger: Logger,
    private val dataDirectory: Path,
) {
    private var runtimeHost: PnLibraryRuntimeHost? = null
    private var playerEvents: VelocityPlayerEvents? = null
    private var linkListener: VelocityLinkListener? = null
    private var networkTask: ScheduledTask? = null

    /**
     * Starts the shared runtime when Velocity announces proxy initialization.
     *
     * @param event lifecycle signal supplied by Velocity; its payload is not required
     */
    @Suppress("UNUSED_PARAMETER")
    fun onInitialize(event: ProxyInitializeEvent) {
        check(runtimeHost == null) { "Velocity runtime is already initialized" }
        val adapter = VelocityPlatformAdapter(
            plugin = owner,
            proxy = server,
            metricsFactory = VelocityMetricsFactory(server, logger, dataDirectory),
            dataFolder = dataDirectory,
            logger = logger,
        )
        val started = PnLibraryRuntimeHost.launchPlatform(
            owner,
            adapter,
            VelocityPlatform::class.java,
            VelocityPlatformServices(server),
            adapter::markReady,
        )
        runtimeHost = started
        val listener = VelocityPlayerEvents(started.library)
        server.eventManager.register(owner, listener)
        playerEvents = listener
        connectLink(started)
    }

    /** Opens the link to the game servers, registers `/pnproxy` on it and, when it is secured, starts announcing this proxy. */
    private fun connectLink(started: PnLibraryRuntimeHost) {
        val transport = LinkTransport { name, bytes ->
            server.getServer(name).map { it.sendPluginMessage(VelocityLinkListener.CHANNEL, bytes) }.orElse(false)
        }
        val network = Link(LinkSecret.readOrCreate(dataDirectory), dataDirectory, transport)
        val control = ProxyControlCommand(started.library, network)
        control.register()
        val data = ProxyMenu.server(started.library, network, control) { instance ->
            instance?.let { server.pluginManager.fromInstance(it).orElse(null)?.description }?.let { description ->
                OwnerInfo(description.name.orElse(description.id), description.version.orElse(null), description.authors)
            }
        }
        val link = VelocityLinkListener(LinkReceiver(network, RemoteExecutor(started.library), data))
        server.channelRegistrar.register(VelocityLinkListener.CHANNEL)
        server.eventManager.register(owner, link)
        linkListener = link
        if (network.enabled) {
            val publisher = NetworkPublisher(started.library, network, { "Velocity ${server.version.version}" }, { server.playerCount })
            networkTask = server.scheduler.buildTask(owner, Runnable {
                val message = publisher.message()
                server.allServers.forEach { runCatchingNonFatal { it.sendPluginMessage(VelocityLinkListener.CHANNEL, message) } }
            }).delay(NETWORK_DELAY_SECONDS, TimeUnit.SECONDS).repeat(NETWORK_PERIOD_SECONDS, TimeUnit.SECONDS).schedule()
        }
    }

    private companion object {
        const val NETWORK_DELAY_SECONDS = 10L
        const val NETWORK_PERIOD_SECONDS = 30L
    }

    /**
     * Closes the runtime before the proxy completes shutdown.
     *
     * @param event lifecycle signal supplied by Velocity; its payload is not required
     */
    @Suppress("UNUSED_PARAMETER")
    fun onShutdown(event: ProxyShutdownEvent) {
        playerEvents?.let { runCatchingNonFatal { server.eventManager.unregisterListener(owner, it) } }
        playerEvents = null
        networkTask?.cancel()
        networkTask = null
        linkListener?.let { runCatchingNonFatal { server.eventManager.unregisterListener(owner, it) } }
        linkListener = null
        runCatchingNonFatal { server.channelRegistrar.unregister(VelocityLinkListener.CHANNEL) }
        val host = runtimeHost
        runtimeHost = null
        host?.close()
    }
}
