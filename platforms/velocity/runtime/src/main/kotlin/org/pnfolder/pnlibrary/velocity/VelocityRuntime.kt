package org.pnfolder.pnlibrary.velocity

import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.proxy.ProxyServer
import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
import org.pnfolder.pnlibrary.core.link.LinkReceiver
import org.pnfolder.pnlibrary.core.link.LinkSecret
import org.pnfolder.pnlibrary.core.runtime.PnLibraryRuntimeHost
import org.pnfolder.pnlibrary.velocity.api.VelocityPlatform
import org.slf4j.Logger
import java.nio.file.Path

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
            VelocityPlatformImpl(server),
            adapter::markReady,
        )
        runtimeHost = started
        val listener = VelocityPlayerEvents(started.library)
        server.eventManager.register(owner, listener)
        playerEvents = listener
        val link = VelocityLinkListener(LinkReceiver(LinkSecret.readOrCreate(dataDirectory)))
        server.channelRegistrar.register(VelocityLinkListener.CHANNEL)
        server.eventManager.register(owner, link)
        linkListener = link
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
        linkListener?.let { runCatchingNonFatal { server.eventManager.unregisterListener(owner, it) } }
        linkListener = null
        runCatchingNonFatal { server.channelRegistrar.unregister(VelocityLinkListener.CHANNEL) }
        val host = runtimeHost
        runtimeHost = null
        host?.close()
    }
}
