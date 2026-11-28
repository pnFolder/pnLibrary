package org.pnfolder.pnlibrary.velocity.metrics;

import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.ProxyServer;
import java.io.File;
import java.nio.file.Path;
import java.util.function.BooleanSupplier;
import org.bstats.json.JsonObjectBuilder;
import org.pnfolder.pnlibrary.api.logging.PnLogger;
import org.pnfolder.pnlibrary.api.metrics.PluginMetrics;
import org.pnfolder.pnlibrary.core.metrics.bstats.BStatsClient;
import org.pnfolder.pnlibrary.core.metrics.bstats.BStatsMetricsSession;
import org.pnfolder.pnlibrary.core.metrics.bstats.BStatsSettings;

/** Opens bStats sessions on Velocity; the sending itself is shared by all platforms. */
final class VelocityBStats {
    private static final String[] NOTICE = {
        "Velocity and some of its plugins collect metrics and send them to bStats (https://bStats.org).",
        "bStats collects some basic information for plugin authors, like how many people use",
        "their plugin and their total player count. It's recommend to keep bStats enabled, but",
        "if you're not comfortable with this, you can opt-out by editing the config.txt file in",
        "the '/plugins/bStats/' folder and setting enabled to false.",
    };

    /** Velocity has no "plugin disabled" state while the proxy runs. */
    private static final BooleanSupplier OWNER_ENABLED = () -> true;

    private final ProxyServer server;
    private final Path dataDirectory;
    private final PnLogger logger;

    VelocityBStats(ProxyServer server, Path dataDirectory, PnLogger logger) {
        this.server = server;
        this.dataDirectory = dataDirectory;
        this.logger = logger;
    }

    /** The session of the plugin instance {@code owner} under {@code projectId}. */
    PluginMetrics open(Object owner, int projectId) {
        BStatsClient client = client(owner, projectId, settings());
        return new BStatsMetricsSession(projectId, client::addCustomChart, client::shutdown);
    }

    private BStatsSettings settings() {
        BStatsSettings settings = BStatsSettings.properties(configFile());
        if (settings.isCreatedNow()) {
            for (String line : NOTICE) {
                logger.info(line);
            }
        }
        return settings;
    }

    /** The file shared by all bStats users, next to the plugin folders: {@code plugins/bStats/config.txt}. */
    private File configFile() {
        return dataDirectory.getParent().resolve("bStats").resolve("config.txt").toFile();
    }

    private String versionOf(Object owner) {
        PluginContainer container = server.getPluginManager().fromInstance(owner)
                .orElseThrow(() -> new IllegalArgumentException("The provided instance is not a plugin"));
        return container.getDescription().getVersion().orElse("unknown");
    }

    private BStatsClient client(Object owner, int projectId, BStatsSettings settings) {
        return new BStatsClient("velocity", projectId, settings, versionOf(owner),
                this::appendProxyData,
                task -> server.getScheduler().buildTask(owner, task).schedule(),
                OWNER_ENABLED,
                logger::warning,
                logger::info);
    }

    private void appendProxyData(JsonObjectBuilder data) {
        data.appendField("playerAmount", server.getPlayerCount());
        data.appendField("managedServers", server.getAllServers().size());
        data.appendField("onlineMode", server.getConfiguration().isOnlineMode() ? 1 : 0);
        data.appendField("velocityVersionVersion", server.getVersion().getVersion());
        data.appendField("velocityVersionName", server.getVersion().getName());
        data.appendField("velocityVersionVendor", server.getVersion().getVendor());
    }
}
