package org.pnfolder.pnlibrary.bungee.metrics;

import dev.faststats.bungee.BungeeContext;
import net.md_5.bungee.api.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.logging.PnLogger;
import org.pnfolder.pnlibrary.api.metrics.PluginMetrics;
import org.pnfolder.pnlibrary.core.metrics.bstats.BStatsClient;
import org.pnfolder.pnlibrary.core.metrics.bstats.BStatsMetricsSession;
import org.pnfolder.pnlibrary.core.metrics.bstats.BStatsSettings;
import org.pnfolder.pnlibrary.core.metrics.factory.NativePluginMetricsFactory;
import org.pnfolder.pnlibrary.core.metrics.faststats.SimpleFastStatsMetricsSession;

import java.io.File;
import java.util.function.Supplier;

/** Creates bStats and FastStats sessions for BungeeCord plugins. */
public final class BungeeMetricsFactory extends NativePluginMetricsFactory<Plugin> {

    /** Creates the factory; {@code pnLogger} is read when a session needs to log and may be set late. */
    public BungeeMetricsFactory(@NotNull Supplier<PnLogger> pnLogger) {
        super(Plugin.class, "BungeeCord",
                (plugin, projectId) -> {
                    BStatsClient client = openBStats(plugin, projectId, pnLogger);
                    return new BStatsMetricsSession(projectId,
                            chart -> client.addCustomChart(chart),
                            () -> client.shutdown());
                },
                (plugin, token) -> (PluginMetrics) new SimpleFastStatsMetricsSession(token,
                        contextToken -> new BungeeContext.Factory(plugin, contextToken)));
    }

    private static BStatsClient openBStats(Plugin plugin, int serviceId, Supplier<PnLogger> pnLogger) {
        File config = new File(new File(plugin.getDataFolder().getParentFile(), "bStats"), "config.yml");
        return new BStatsClient("bungeecord", serviceId, BStatsSettings.yaml(config),
                plugin.getDescription().getVersion(),
                data -> {
                    data.appendField("playerAmount", plugin.getProxy().getOnlineCount());
                    data.appendField("managedServers", plugin.getProxy().getServers().size());
                    data.appendField("onlineMode", plugin.getProxy().getConfig().isOnlineMode() ? 1 : 0);
                    data.appendField("bungeecordVersion", plugin.getProxy().getVersion());
                    data.appendField("bungeecordName", plugin.getProxy().getName());
                },
                null,
                () -> true,
                (message, error) -> pnLogger.get().warning(message, error),
                message -> pnLogger.get().info(message));
    }
}
