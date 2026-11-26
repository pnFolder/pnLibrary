package org.pnfolder.pnlibrary.velocity.metrics;

import java.util.Objects;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.ProxyServer;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.logging.PnLogger;
import org.pnfolder.pnlibrary.api.metrics.PluginMetrics;
import org.pnfolder.pnlibrary.core.metrics.bstats.BStatsClient;
import org.pnfolder.pnlibrary.core.metrics.bstats.BStatsMetricsSession;
import org.pnfolder.pnlibrary.core.metrics.bstats.BStatsSettings;
import org.pnfolder.pnlibrary.core.metrics.factory.ProviderMetricsFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.function.Supplier;

/** Creates bStats and FastStats sessions for Velocity using explicit platform adapters. */
public final class VelocityMetricsFactory extends ProviderMetricsFactory {
    private static final String FASTSTATS_PROVIDER =
            "org.pnfolder.pnlibrary.velocity.faststats.FastStatsVelocityProvider";

    private final Logger nativeLogger = LoggerFactory.getLogger(VelocityMetricsFactory.class);
    private final ProxyServer server;
    private final Path dataDirectory;
    private final Supplier<PnLogger> pnLogger;

    /**
     * Creates the factory.
     *
     * @param pnLogger provides the library logger for user-visible messages; resolved after the host is built
     */
    public VelocityMetricsFactory(
            @NotNull ProxyServer server, @NotNull Path dataDirectory, @NotNull Supplier<PnLogger> pnLogger) {
        this.server = Objects.requireNonNull(server, "server");
        this.dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory");
        this.pnLogger = Objects.requireNonNull(pnLogger, "pnLogger");
    }

    /** Opens a bStats session for the plugin instance {@code owner} under {@code projectId}. */
    @NotNull
    @Override
    protected PluginMetrics openBStats(@NotNull Object owner, int projectId) {
        BStatsClient client = openBStats(owner, projectId, server, pnLogger.get(), dataDirectory);
        return new BStatsMetricsSession(
            projectId,
            client::addCustomChart,
            client::shutdown
        );
    }

    /**
     * FastStats on Velocity is compiled for Java 21. It is loaded by name and never referenced directly, so a
     * Java 17 proxy never links it: the check below rejects the run before the class is touched.
     */
    @NotNull
    @Override
    protected PluginMetrics openFastStats(@NotNull Object owner, @NotNull String token) {
        if (Runtime.version().feature() < 21) {
            throw new IllegalStateException("FastStats on Velocity requires Java 21 or newer");
        }
        try {
            Class<?> type = Class.forName(FASTSTATS_PROVIDER, true, getClass().getClassLoader());
            Object provider = type.getConstructor().newInstance();
            return (PluginMetrics) type.getMethod("open", Object.class, ProxyServer.class, Logger.class, Path.class,
                    String.class).invoke(provider, owner, server, nativeLogger, dataDirectory, token);
        } catch (InvocationTargetException error) {
            return sneakyThrow(error.getTargetException());
        } catch (ReflectiveOperationException error) {
            return sneakyThrow(error);
        }
    }

    // The overridden method declares no checked exceptions, and the cause must reach the caller unchanged.
    @SuppressWarnings("unchecked")
    private static <E extends Throwable, R> R sneakyThrow(Throwable error) throws E {
        throw (E) error;
    }

    /** Opens the bStats sender for the plugin instance {@code owner}; the sending itself is shared by all platforms. */
    private static BStatsClient openBStats(
            Object owner, int serviceId, ProxyServer server, PnLogger log, Path dataDirectory) {
        PluginContainer container = server.getPluginManager().fromInstance(owner)
                .orElseThrow(() -> new IllegalArgumentException("The provided instance is not a plugin"));
        BStatsSettings settings = BStatsSettings.properties(
                dataDirectory.getParent().resolve("bStats").resolve("config.txt").toFile());
        if (settings.isCreatedNow()) {
            logNotice(log);
        }
        return new BStatsClient("velocity", serviceId, settings, container.getDescription().getVersion().orElse("unknown"),
                data -> {
                    data.appendField("playerAmount", server.getPlayerCount());
                    data.appendField("managedServers", server.getAllServers().size());
                    data.appendField("onlineMode", server.getConfiguration().isOnlineMode() ? 1 : 0);
                    data.appendField("velocityVersionVersion", server.getVersion().getVersion());
                    data.appendField("velocityVersionName", server.getVersion().getName());
                    data.appendField("velocityVersionVendor", server.getVersion().getVendor());
                },
                task -> server.getScheduler().buildTask(owner, task).schedule(),
                () -> true,
                (message, error) -> log.warning(message, error),
                message -> log.info(message));
    }

    private static void logNotice(PnLogger log) {
        log.info("Velocity and some of its plugins collect metrics and send them to bStats (https://bStats.org).");
        log.info("bStats collects some basic information for plugin authors, like how many people use");
        log.info("their plugin and their total player count. It's recommend to keep bStats enabled, but");
        log.info("if you're not comfortable with this, you can opt-out by editing the config.txt file in");
        log.info("the '/plugins/bStats/' folder and setting enabled to false.");
    }
}
