package org.pnfolder.pnlibrary.velocity.faststats

import com.velocitypowered.api.proxy.ProxyServer
import dev.faststats.velocity.VelocityContext
import org.pnfolder.pnlibrary.api.metrics.PluginMetrics
import org.pnfolder.pnlibrary.core.metrics.faststats.SimpleFastStatsMetricsSession
import org.slf4j.Logger
import java.nio.file.Path

/** Velocity adapter that supplies a Velocity context to the shared FastStats session. */
class FastStatsVelocityProvider {
    /** Opens a FastStats session with [token] for the plugin instance [owner]; it must be registered with [server]. */
    fun open(
        owner: Any,
        server: ProxyServer,
        logger: Logger,
        dataDirectory: Path,
        token: String,
    ): PluginMetrics = SimpleFastStatsMetricsSession(token) { contextToken ->
        val container = server.pluginManager.fromInstance(owner).orElseThrow()
        VelocityContext.Factory(container, server, logger, dataDirectory).token(contextToken)
    }
}
