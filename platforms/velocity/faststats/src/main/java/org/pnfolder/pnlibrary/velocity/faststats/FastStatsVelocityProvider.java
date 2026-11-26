package org.pnfolder.pnlibrary.velocity.faststats;

import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.faststats.velocity.VelocityContext;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.metrics.PluginMetrics;
import org.pnfolder.pnlibrary.core.metrics.faststats.SimpleFastStatsMetricsSession;
import org.slf4j.Logger;

import java.nio.file.Path;

/** Velocity adapter that supplies a Velocity context to the shared FastStats session. */
public final class FastStatsVelocityProvider {

    /** Opens a FastStats session with {@code token} for the plugin instance {@code owner} registered with {@code server}. */
    @NotNull
    public PluginMetrics open(
            @NotNull Object owner,
            @NotNull ProxyServer server,
            @NotNull Logger logger,
            @NotNull Path dataDirectory,
            @NotNull String token) {
        return new SimpleFastStatsMetricsSession(token, contextToken -> {
            PluginContainer container = server.getPluginManager().fromInstance(owner).orElseThrow();
            return new VelocityContext.Factory(container, server, logger, dataDirectory).token(contextToken);
        });
    }
}
