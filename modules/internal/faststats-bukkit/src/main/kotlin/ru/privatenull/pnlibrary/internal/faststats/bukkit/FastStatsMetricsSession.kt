package ru.privatenull.pnlibrary.internal.faststats.bukkit

import dev.faststats.bukkit.BukkitContext
import org.bukkit.plugin.Plugin
import ru.privatenull.pnlibrary.internal.faststats.core.AbstractFastStatsMetricsSession
import ru.privatenull.pnlibrary.internal.faststats.core.FastStatsContext
import ru.privatenull.pnlibrary.internal.faststats.core.FastStatsContextFactory

/** Bukkit adapter that supplies a Bukkit context to the shared FastStats session. */
class FastStatsMetricsSession(plugin: Plugin, token: String) : AbstractFastStatsMetricsSession(
    token = token,
    contextFactory = FastStatsContextFactory { contextToken, tracker, metrics ->
        BukkitContext.Factory(plugin, contextToken)
            .errorTrackerService(tracker)
            .metrics(metrics)
            .create()
            .asPnLibraryContext()
    },
)

private fun BukkitContext.asPnLibraryContext(): FastStatsContext = object : FastStatsContext {
    override fun ready() = this@asPnLibraryContext.ready()
    override fun shutdown() = this@asPnLibraryContext.shutdown()
}
