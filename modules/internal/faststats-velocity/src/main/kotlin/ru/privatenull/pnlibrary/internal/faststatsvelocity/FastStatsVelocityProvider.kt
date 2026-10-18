package ru.privatenull.pnlibrary.internal.faststatsvelocity

import com.velocitypowered.api.proxy.ProxyServer
import dev.faststats.velocity.VelocityContext
import org.slf4j.Logger
import ru.privatenull.pnlibrary.api.metrics.PluginMetrics
import ru.privatenull.pnlibrary.core.metrics.faststats.SimpleFastStatsMetricsSession
import java.nio.file.Path

/** Velocity adapter that supplies a Velocity context to the shared FastStats session. */
class FastStatsVelocityProvider {
    /** Opens a FastStats session for the Velocity plugin instance [owner]. */
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
