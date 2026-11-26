package org.pnfolder.pnlibrary.bukkit.integration;

import java.util.function.Supplier;

import dev.faststats.bukkit.BukkitContext;
import org.bukkit.plugin.Plugin;
import org.pnfolder.pnlibrary.api.logging.PnLogger;
import org.pnfolder.pnlibrary.core.metrics.bstats.BStatsClient;
import org.pnfolder.pnlibrary.core.metrics.bstats.BStatsMetricsSession;
import org.pnfolder.pnlibrary.core.metrics.factory.NativePluginMetricsFactory;
import org.pnfolder.pnlibrary.core.metrics.faststats.SimpleFastStatsMetricsSession;

/** Opens independently managed bStats and FastStats sessions for Bukkit plugins. */
public final class BukkitMetricsFactory extends NativePluginMetricsFactory<Plugin> {
    public BukkitMetricsFactory(Supplier<PnLogger> pnLogger) {
        super(Plugin.class, "Bukkit",
            (plugin, projectId) -> {
                BStatsClient client = BukkitBStats.open(plugin, projectId, pnLogger);
                return new BStatsMetricsSession(projectId, chart -> client.addCustomChart(chart), () -> client.shutdown());
            },
            (plugin, token) -> new SimpleFastStatsMetricsSession(token, contextToken ->
                new BukkitContext.Factory(plugin, contextToken)));
    }
}
