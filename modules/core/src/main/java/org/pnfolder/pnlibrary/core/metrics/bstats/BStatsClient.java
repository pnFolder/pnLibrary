package org.pnfolder.pnlibrary.core.metrics.bstats;

import org.bstats.MetricsBase;
import org.bstats.charts.CustomChart;
import org.bstats.json.JsonObjectBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** The one bStats sender for every platform. Collecting charts, timing and submitting stay in {@link MetricsBase}. */
public final class BStatsClient {
    private final MetricsBase base;

    /** A client that sends from its own thread and is always enabled for its owner. */
    public BStatsClient(@NotNull String platform, int serviceId, @NotNull BStatsSettings settings,
                        @NotNull String pluginVersion, @NotNull Consumer<JsonObjectBuilder> appendPlatformData,
                        @NotNull BiConsumer<String, Throwable> warn, @NotNull Consumer<String> info) {
        this(platform, serviceId, settings, pluginVersion, appendPlatformData, null, () -> true, warn, info);
    }

    /**
     * Creates a client.
     *
     * @param appendPlatformData adds the platform fields to every submission
     * @param mainThread runs a task on the server thread, or {@code null} to run it where bStats is
     * @param ownerEnabled whether the owning plugin is still enabled
     */
    public BStatsClient(@NotNull String platform, int serviceId, @NotNull BStatsSettings settings,
                        @NotNull String pluginVersion, @NotNull Consumer<JsonObjectBuilder> appendPlatformData,
                        @Nullable Consumer<Runnable> mainThread, @NotNull BooleanSupplier ownerEnabled,
                        @NotNull BiConsumer<String, Throwable> warn, @NotNull Consumer<String> info) {
        this.base = new MetricsBase(
                platform,
                settings.getServerUuid(),
                serviceId,
                settings.isEnabled(),
                builder -> {
                    appendPlatformData.accept(builder);
                    builder.appendField("javaVersion", System.getProperty("java.version"));
                    builder.appendField("osName", System.getProperty("os.name"));
                    builder.appendField("osArch", System.getProperty("os.arch"));
                    builder.appendField("osVersion", System.getProperty("os.version"));
                    builder.appendField("coreCount", Runtime.getRuntime().availableProcessors());
                },
                builder -> builder.appendField("pluginVersion", pluginVersion),
                mainThread,
                ownerEnabled::getAsBoolean,
                warn,
                info,
                settings.isLogFailedRequests(),
                settings.isLogSentData(),
                settings.isLogResponseStatusText(),
                false);
    }

    /** Adds a chart to every later submission. */
    public void addCustomChart(@NotNull CustomChart chart) {
        base.addCustomChart(chart);
    }

    /** Stops submitting. */
    public void shutdown() {
        base.shutdown();
    }
}
