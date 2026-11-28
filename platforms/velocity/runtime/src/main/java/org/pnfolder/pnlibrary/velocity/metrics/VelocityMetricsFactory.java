package org.pnfolder.pnlibrary.velocity.metrics;

import com.velocitypowered.api.proxy.ProxyServer;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.logging.PnLogger;
import org.pnfolder.pnlibrary.api.metrics.PluginMetrics;
import org.pnfolder.pnlibrary.core.metrics.factory.ProviderMetricsFactory;

/** Creates bStats and FastStats sessions for Velocity plugins. */
public final class VelocityMetricsFactory extends ProviderMetricsFactory {
    private final ProxyServer server;
    private final Path dataDirectory;
    private final Supplier<PnLogger> logger;
    private final VelocityFastStats fastStats;

    /**
     * Creates the factory.
     *
     * @param logger the library logger for what the user should read; asked when a session opens, so it may exist only
     *               after the library has started
     */
    public VelocityMetricsFactory(@NotNull ProxyServer server, @NotNull Path dataDirectory, @NotNull Supplier<PnLogger> logger) {
        this.server = Objects.requireNonNull(server, "server");
        this.dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.fastStats = new VelocityFastStats(server, dataDirectory);
    }

    @NotNull
    @Override
    protected PluginMetrics openBStats(@NotNull Object owner, int projectId) {
        return new VelocityBStats(server, dataDirectory, logger.get()).open(owner, projectId);
    }

    @NotNull
    @Override
    protected PluginMetrics openFastStats(@NotNull Object owner, @NotNull String token) {
        return fastStats.open(owner, token);
    }
}
