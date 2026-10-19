package org.pnfolder.pnlibrary.velocity

import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.ProxyServer
import org.bstats.velocity.Metrics
import org.slf4j.Logger
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity
import org.pnfolder.pnlibrary.core.runtime.PnLibraryRuntimeHost
import org.pnfolder.pnlibrary.velocity.api.VelocityPlatform
import java.nio.file.Path

/**
 * Velocity lifecycle entry point for pnLibrary.
 *
 * Velocity injects the proxy, logger, metrics factory, and plugin data directory. The initialize
 * event creates one [PnLibraryRuntimeHost]; the matching shutdown event closes all shared and
 * platform resources owned by that host.
 */
@Plugin(
    id = PnLibraryIdentity.PRODUCT_ID,
    name = PnLibraryIdentity.NAME,
    authors = ["privatenull", "inventorytype"],
    description = PnLibraryIdentity.DESCRIPTION,
    url = PnLibraryIdentity.REPOSITORY_URL,
)
class PnLibraryVelocityPlugin @Inject constructor(
    private val server: ProxyServer,
    private val logger: Logger,
    private val metricsFactory: Metrics.Factory,
    @DataDirectory
    private val dataDirectory: Path,
) {
    private var runtimeHost: PnLibraryRuntimeHost? = null

    /**
     * Starts the shared runtime when Velocity announces proxy initialization.
     *
     * @param event lifecycle signal supplied by Velocity; its payload is not required
     */
    @Subscribe
    @Suppress("UNUSED_PARAMETER")
    fun onInitialize(event: ProxyInitializeEvent) {
        check(runtimeHost == null) { "Velocity runtime is already initialized" }
        val adapter = VelocityPlatformAdapter(
            plugin = this,
            proxy = server,
            metricsFactory = VelocityMetricsFactory(metricsFactory, server, logger, dataDirectory),
            dataFolder = dataDirectory,
            logger = logger,
        )
        runtimeHost = PnLibraryRuntimeHost.launchPlatform(
            this,
            adapter,
            VelocityPlatform::class.java,
            VelocityPlatformImpl(server),
            adapter::markReady,
        )
    }

    /**
     * Closes the runtime before the proxy completes shutdown.
     *
     * @param event lifecycle signal supplied by Velocity; its payload is not required
     */
    @Subscribe
    @Suppress("UNUSED_PARAMETER")
    fun onShutdown(event: ProxyShutdownEvent) {
        val host = runtimeHost
        runtimeHost = null
        host?.close()
    }
}
