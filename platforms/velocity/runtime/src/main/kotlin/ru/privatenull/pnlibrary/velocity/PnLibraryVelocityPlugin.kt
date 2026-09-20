package ru.privatenull.pnlibrary.velocity

import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.ProxyServer
import org.bstats.velocity.Metrics
import org.slf4j.Logger
import ru.privatenull.pnlibrary.velocity.api.VelocityPlatform
import ru.privatenull.pnlibrary.core.runtime.PnLibraryRuntimeHost
import java.nio.file.Path

/**
 * Velocity lifecycle entry point for pnLibrary.
 *
 * Velocity injects the proxy, logger, metrics factory, and plugin data directory. The initialize
 * event creates one [PnLibraryRuntimeHost]; the matching shutdown event closes all shared and
 * platform resources owned by that host.
 */
@Plugin(
    id = "pnlibrary",
    name = "pnLibrary",
    authors = ["pnFolder"]
)
class PnLibraryVelocityPlugin @Inject constructor(
    private val server: ProxyServer,
    private val logger: Logger,
    private val metricsFactory: Metrics.Factory,
    @DataDirectory private val dataDirectory: Path,
) {
    private var runtimeHost: PnLibraryRuntimeHost? = null

    /**
     * Starts the shared runtime when Velocity announces proxy initialization.
     *
     * @param event lifecycle signal supplied by Velocity; its payload is not required
     */
    @Subscribe
    fun onInitialize(event: ProxyInitializeEvent) {
        val adapter = VelocityPlatformAdapter(this, server, VelocityMetricsFactory(metricsFactory), dataDirectory, logger)
        runtimeHost = PnLibraryRuntimeHost.start(
            this,
            adapter,
            dataDirectory.parent.resolve("update"),
        ).also { host ->
            host.registerPlatform(VelocityPlatform::class.java, VelocityPlatformImpl(server))
        }
    }

    /**
     * Closes the runtime before the proxy completes shutdown.
     *
     * @param event lifecycle signal supplied by Velocity; its payload is not required
     */
    @Subscribe
    fun onShutdown(event: ProxyShutdownEvent) {
        runtimeHost?.close()
        runtimeHost = null
    }
}
