package org.pnfolder.pnlibrary.velocity

import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.scheduler.ScheduledTask
import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
import org.pnfolder.pnlibrary.core.menu.OwnerInfo
import org.pnfolder.pnlibrary.core.proxy.ProxyLink
import org.pnfolder.pnlibrary.core.runtime.PnLibraryRuntimeHost
import org.pnfolder.pnlibrary.core.updates.catalog.ProxyUpdateReminder
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
    private var link: ProxyLink? = null
    private var networkTask: ScheduledTask? = null
    private var reminderTask: ScheduledTask? = null

    /**
     * Starts the shared runtime when Velocity announces proxy initialization.
     *
     * @param event lifecycle signal supplied by Velocity; its payload is not required
     */
    @Suppress("UNUSED_PARAMETER")
    fun onInitialize(event: ProxyInitializeEvent) {
        check(runtimeHost == null) { "Velocity runtime is already initialized" }
        try {
            open()
        } catch (error: Throwable) {
            // Whatever opened before the failure must not stay registered on a proxy that goes on without us.
            runCatchingNonFatal(::close)
            throw error
        }
    }

    private fun open() {
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
        remindAdministrators(started)
    }

    /** Repeats the update notice in chat to administrators; the interval is fixed in code and cannot be turned off. */
    private fun remindAdministrators(started: PnLibraryRuntimeHost) {
        reminderTask = server.scheduler.buildTask(owner, Runnable {
            server.allPlayers.filter { ProxyUpdateReminder.canSee(it::hasPermission) }.forEach { ProxyUpdateReminder.send(started.library, it.uniqueId) }
        }).delay(ProxyUpdateReminder.INTERVAL_SECONDS, TimeUnit.SECONDS).repeat(ProxyUpdateReminder.INTERVAL_SECONDS, TimeUnit.SECONDS).schedule()
    }

    /** Opens the link to the game servers and, when it is secured, starts announcing this proxy. */
    private fun connectLink(started: PnLibraryRuntimeHost) {
        val proxyLink = ProxyLink(
            library = started.library,
            folder = dataDirectory,
            warn = logger::warn,
            transport = { name, bytes ->
                server.getServer(name).map { it.sendPluginMessage(VelocityLinkListener.CHANNEL, bytes) }.orElse(false)
            },
            describe = { instance ->
                instance?.let { server.pluginManager.fromInstance(it).orElse(null)?.description }?.let { description ->
                    OwnerInfo(description.name.orElse(description.id), description.version.orElse(null), description.authors)
                }
            },
            holds = { viewer, permission -> server.getPlayer(viewer).map { it.hasPermission(permission) }.orElse(false) },
            platformName = { "Velocity ${server.version.version}" },
            players = { server.playerCount },
            broadcast = { message -> server.allServers.forEach { runCatchingNonFatal { it.sendPluginMessage(VelocityLinkListener.CHANNEL, message) } } },
        )
        link = proxyLink
        val listener = VelocityLinkListener(proxyLink.receiver)
        server.channelRegistrar.register(VelocityLinkListener.CHANNEL)
        server.eventManager.register(owner, listener)
        linkListener = listener
        if (proxyLink.announces) {
            networkTask = server.scheduler.buildTask(owner, Runnable(proxyLink::announce))
                .delay(ProxyLink.NETWORK_DELAY_SECONDS, TimeUnit.SECONDS)
                .repeat(ProxyLink.NETWORK_PERIOD_SECONDS, TimeUnit.SECONDS)
                .schedule()
        }
    }

    /**
     * Closes the runtime before the proxy completes shutdown.
     *
     * @param event lifecycle signal supplied by Velocity; its payload is not required
     */
    @Suppress("UNUSED_PARAMETER")
    fun onShutdown(event: ProxyShutdownEvent) = close()

    private fun close() {
        playerEvents?.let { runCatchingNonFatal { server.eventManager.unregisterListener(owner, it) } }
        playerEvents = null
        networkTask?.cancel()
        reminderTask?.cancel()
        reminderTask = null
        networkTask = null
        linkListener?.let { runCatchingNonFatal { server.eventManager.unregisterListener(owner, it) } }
        linkListener = null
        link?.let { runCatchingNonFatal(it::close) }
        link = null
        runCatchingNonFatal { server.channelRegistrar.unregister(VelocityLinkListener.CHANNEL) }
        val host = runtimeHost
        runtimeHost = null
        host?.close()
    }
}
