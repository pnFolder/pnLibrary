package org.pnfolder.pnlibrary.bukkit.integration;

import java.io.File;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.pnfolder.pnlibrary.api.logging.PnLogger;
import org.pnfolder.pnlibrary.bukkit.compat.ServerCapabilities;
import org.pnfolder.pnlibrary.bukkit.tasks.ServerScheduler;
import org.pnfolder.pnlibrary.core.metrics.bstats.BStatsClient;
import org.pnfolder.pnlibrary.core.metrics.bstats.BStatsSettings;

/** What bStats needs to know about a Bukkit server; the sending itself is shared by every platform. */
final class BukkitBStats {
    private BukkitBStats() {
    }

    /** Creates the bStats client for {@code plugin} with the Bukkit-specific fields and thread hand-off. */
    static BStatsClient open(Plugin plugin, int serviceId, Supplier<PnLogger> pnLogger) {
        // Folia has no single main thread to hand the task to, see https://github.com/Bastian/bstats-metrics/pull/126
        ServerScheduler scheduler = new ServerScheduler(plugin);
        return new BStatsClient(
            "bukkit",
            serviceId,
            BStatsSettings.yaml(new File(new File(plugin.getDataFolder().getParentFile(), "bStats"), "config.yml")),
            plugin.getDescription().getVersion(),
            data -> {
                data.appendField("playerAmount", Bukkit.getOnlinePlayers().size());
                data.appendField("onlineMode", Bukkit.getOnlineMode() ? 1 : 0);
                data.appendField("bukkitVersion", Bukkit.getVersion());
                data.appendField("bukkitName", Bukkit.getName());
            },
            ServerCapabilities.isFolia() ? null : scheduler::nextTick,
            plugin::isEnabled,
            (message, error) -> pnLogger.get().warning(message, error),
            message -> pnLogger.get().info(message));
    }
}
