package ru.privatenull.pnlibrary.bungee.metrics

import dev.faststats.bungee.BungeeContext
import net.md_5.bungee.api.plugin.Plugin
import ru.privatenull.pnlibrary.internal.faststats.core.AbstractFastStatsMetricsSession
import ru.privatenull.pnlibrary.internal.faststats.core.FastStatsContext
import ru.privatenull.pnlibrary.internal.faststats.core.FastStatsContextFactory

/** BungeeCord adapter that supplies a Bungee context to the shared FastStats session. */
internal class FastStatsMetricsSession(plugin: Plugin, token: String) : AbstractFastStatsMetricsSession(
    token = token,
    contextFactory = FastStatsContextFactory { contextToken, tracker, metrics ->
        BungeeContext.Factory(plugin, contextToken)
            .errorTrackerService(tracker)
            .metrics(metrics)
            .create()
            .asPnLibraryContext()
    },
)

private fun BungeeContext.asPnLibraryContext(): FastStatsContext = object : FastStatsContext {
    override fun ready() = this@asPnLibraryContext.ready()
    override fun shutdown() = this@asPnLibraryContext.shutdown()
}
