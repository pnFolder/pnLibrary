package org.pnfolder.pnlibrary.velocity

import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.proxy.ProxyServer
import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
import org.pnfolder.pnlibrary.core.exceptions.ResourceCleanup
import org.pnfolder.pnlibrary.core.link.LinkReceiver
import org.pnfolder.pnlibrary.api.logging.PnLogger
import org.pnfolder.pnlibrary.core.runtime.PnLibraryRuntime
import org.pnfolder.pnlibrary.core.runtime.PnLibraryRuntimeHost
import org.pnfolder.pnlibrary.core.runtime.ProxyRuntime
import org.pnfolder.pnlibrary.velocity.api.VelocityPlatform
import org.pnfolder.pnlibrary.velocity.link.VelocityLinkListener
import org.pnfolder.pnlibrary.velocity.metrics.VelocityMetricsFactory
import org.pnfolder.pnlibrary.velocity.network.VelocityPlayerEvents
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
    private val dataDirectory: Path,
) {
    private var proxyRuntime: ProxyRuntime? = null
    private var runtimeHost: PnLibraryRuntimeHost? = null
    private var playerEvents: VelocityPlayerEvents? = null

    /** Starts the shared runtime when Velocity announces proxy initialization. */
    fun onInitialize(@Suppress("UNUSED_PARAMETER") event: ProxyInitializeEvent) {
        check(runtimeHost == null) { "Velocity runtime is already initialized" }
        try {
            open()
        } catch (error: Throwable) {
            runCatchingNonFatal(::close).onFailure(error::addSuppressed)
            throw error
        }
    }

    private fun open() {
        var metricsLogger: PnLogger? = null
        val adapter = VelocityPlatformAdapter(
            plugin = owner,
            proxy = server,
            metricsFactory = VelocityMetricsFactory(server, dataDirectory) { metricsLogger!! },
            dataFolder = dataDirectory,
        )
        val host = PnLibraryRuntime.builder()
            .owner(owner)
            .adapter(adapter)
            .platform<VelocityPlatform>(object : VelocityPlatform { override val proxy get() = server })
            .launch(adapter::markReady)
        runtimeHost = host
        metricsLogger = host.library.logging.logger(owner, "Metrics")

        val proxy = ProxyRuntime(adapter, ::registerLinkListener, adapter.networkProxy::describePlugin)
        proxyRuntime = proxy
        proxy.start(host)

        playerEvents = VelocityPlayerEvents(host.library).also { server.eventManager.register(owner, it) }
    }

    private fun registerLinkListener(receiver: LinkReceiver): AutoCloseable {
        val listener = VelocityLinkListener(receiver)
        server.eventManager.register(owner, listener)
        return AutoCloseable { server.eventManager.unregisterListener(owner, listener) }
    }

    /** Closes the runtime before the proxy completes shutdown. */
    fun onShutdown(@Suppress("UNUSED_PARAMETER") event: ProxyShutdownEvent) = close()

    private fun close() {
        val events = playerEvents
        val proxy = proxyRuntime
        val host = runtimeHost
        playerEvents = null
        proxyRuntime = null
        runtimeHost = null

        ResourceCleanup.closeAll(
            { events?.let { server.eventManager.unregisterListener(owner, it) } },
            { proxy?.close() },
            { host?.close() },
        )
    }
}
