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
import ru.privatenull.pnlibrary.api.PnLibrary
import ru.privatenull.pnlibrary.core.PnLibraryBootstrap
import ru.privatenull.pnlibrary.core.PnLibraryImpl
import ru.privatenull.pnlibrary.core.MandatoryUpdateService
import java.nio.file.Paths
import java.nio.file.Path

@Plugin(id = "pnlibrary", name = "pnLibrary", version = "2.0.0-beta.2", authors = ["pnFolder"])
class PnLibraryVelocityPlugin @Inject constructor(
    private val server: ProxyServer,
    private val logger: Logger,
    private val metricsFactory: Metrics.Factory,
    @DataDirectory private val dataDirectory: Path,
) {
    private var runtime: PnLibrary? = null

    @Subscribe
    fun onInitialize(event: ProxyInitializeEvent) {
        val adapter = VelocityPlatformAdapter(this, server, VelocityMetricsFactory(metricsFactory), dataDirectory, logger)
        val loaded = PnLibraryBootstrap.bootstrap(this, adapter)
        adapter.attachLibrary(loaded as PnLibraryImpl)
        runtime = loaded
        MandatoryUpdateService.start(this, adapter, "2.0.0-beta.2", "velocity",
            Paths.get(javaClass.protectionDomain.codeSource.location.toURI()),
            dataDirectory.parent.resolve("update"))
        logger.info("pnLibrary enabled (velocity)")
    }

    @Subscribe
    fun onShutdown(event: ProxyShutdownEvent) {
        runtime?.close()
        runtime = null
    }
}
