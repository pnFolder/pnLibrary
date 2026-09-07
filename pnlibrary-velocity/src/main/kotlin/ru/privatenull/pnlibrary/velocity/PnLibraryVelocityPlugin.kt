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
import ru.privatenull.pnlibrary.api.runtime.PnLibrary
import ru.privatenull.pnlibrary.core.runtime.PnLibraryBootstrap
import ru.privatenull.pnlibrary.core.runtime.PnLibraryImpl
import ru.privatenull.pnlibrary.core.runtime.PnLibraryConfigLoader
import ru.privatenull.pnlibrary.core.updates.MandatoryUpdateService
import java.nio.file.Paths
import java.nio.file.Path

@Plugin(id = "pnlibrary", name = "pnLibrary", authors = ["pnFolder"])
class PnLibraryVelocityPlugin @Inject constructor(
    private val server: ProxyServer,
    private val logger: Logger,
    private val metricsFactory: Metrics.Factory,
    @DataDirectory private val dataDirectory: Path,
) {
    private var runtime: PnLibrary? = null
    private var updateMonitor: AutoCloseable? = null

    @Subscribe
    fun onInitialize(event: ProxyInitializeEvent) {
        val adapter = VelocityPlatformAdapter(this, server, VelocityMetricsFactory(metricsFactory), dataDirectory, logger)
        val loaded = PnLibraryBootstrap.bootstrap(this, adapter, PnLibraryConfigLoader.load(dataDirectory))
        adapter.attachLibrary(loaded as PnLibraryImpl)
        runtime = loaded
        val currentVersion = adapter.ownerDetails(this)["version"]
            ?: error("Velocity did not expose the pnLibrary version")
        updateMonitor = MandatoryUpdateService.start(this, adapter, currentVersion, "velocity",
            Paths.get(javaClass.protectionDomain.codeSource.location.toURI()),
            dataDirectory.parent.resolve("update"))
        logger.info("pnLibrary enabled (velocity)")
    }

    @Subscribe
    fun onShutdown(event: ProxyShutdownEvent) {
        runCatching { updateMonitor?.close() }
        updateMonitor = null
        runtime?.close()
        runtime = null
    }
}
