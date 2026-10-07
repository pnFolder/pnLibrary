package ru.privatenull.pnlibrary.internal.faststatsvelocity

import com.velocitypowered.api.plugin.PluginContainer
import com.velocitypowered.api.proxy.ProxyServer
import dev.faststats.velocity.VelocityContext
import org.slf4j.Logger
import ru.privatenull.pnlibrary.api.metrics.PluginMetrics
import ru.privatenull.pnlibrary.internal.faststats.core.AbstractFastStatsMetricsSession
import ru.privatenull.pnlibrary.internal.faststats.core.FastStatsContext
import ru.privatenull.pnlibrary.internal.faststats.core.FastStatsContextFactory
import java.nio.file.Path

/** Velocity adapter that supplies a Velocity context to the shared FastStats session. */
class FastStatsVelocityProvider {
    fun open(
        owner: Any,
        server: ProxyServer,
        logger: Logger,
        dataDirectory: Path,
        token: String,
    ): PluginMetrics = Session(owner, server, logger, dataDirectory, token)

    private class Session(
        owner: Any,
        server: ProxyServer,
        logger: Logger,
        dataDirectory: Path,
        token: String,
    ) : AbstractFastStatsMetricsSession(
        token = token,
        contextFactory = FastStatsContextFactory { contextToken, tracker, metrics ->
            val container: PluginContainer = server.pluginManager.fromInstance(owner).orElseThrow()
            VelocityContext.Factory(container, server, logger, dataDirectory)
                .token(contextToken)
                .errorTrackerService(tracker)
                .metrics(metrics)
                .create()
                .asPnLibraryContext()
        },
    )
}

private fun VelocityContext.asPnLibraryContext(): FastStatsContext = object : FastStatsContext {
    override fun ready() = this@asPnLibraryContext.ready()
    override fun shutdown() = this@asPnLibraryContext.shutdown()
}
