package org.pnfolder.pnlibrary.core.runtime.details;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.metrics.PluginMetrics;
import org.pnfolder.pnlibrary.api.metrics.provider.MetricsProvider;
import org.pnfolder.pnlibrary.api.metrics.provider.MetricsProviderConfiguration;
import org.pnfolder.pnlibrary.api.platform.PlatformType;
import org.pnfolder.pnlibrary.common.lang.Failures;
import org.pnfolder.pnlibrary.core.metrics.session.MetricsSessions;
import org.pnfolder.pnlibrary.spi.platform.PlatformIdentity;

/** Starts the library's own metrics session and reports failure as a warning instead of failing startup. */
public final class RuntimeMetricsBootstrap {
    // bStats keeps one project per platform.
    private static final Map<PlatformType, Integer> BSTATS_PROJECT_IDS = new EnumMap<PlatformType, Integer>(PlatformType.class);
    private static final String FASTSTATS_TOKEN = "f42221b1c859605f4c717b427f5df910";

    static {
        BSTATS_PROJECT_IDS.put(PlatformType.BUKKIT, 34864);
        BSTATS_PROJECT_IDS.put(PlatformType.BUNGEECORD, 35054);
        BSTATS_PROJECT_IDS.put(PlatformType.VELOCITY, 35053);
    }

    private final MetricsSessions registry;
    private final PlatformIdentity platform;
    private final Object owner;
    private final Consumer<String> warning;

    public RuntimeMetricsBootstrap(@NotNull MetricsSessions registry, @NotNull PlatformIdentity platform, @NotNull Object owner,
                                   @NotNull Consumer<String> warning) {
        this.registry = registry;
        this.platform = platform;
        this.owner = owner;
        this.warning = warning;
    }

    public @Nullable PluginMetrics start() {
        final PluginMetrics[] opened = new PluginMetrics[1];
        return Failures.runCatchingNonFatal(() -> {
            PluginMetrics metrics = registry.open(owner, Arrays.asList(
                    new MetricsProviderConfiguration(MetricsProvider.BSTATS, true, BSTATS_PROJECT_IDS.get(PlatformType.BUKKIT),
                            BSTATS_PROJECT_IDS, null),
                    MetricsProviderConfiguration.fastStats(FASTSTATS_TOKEN)));
            opened[0] = metrics;
            metrics.simplePie("platform", () -> platform.getType().name().toLowerCase(Locale.ROOT));
            metrics.simplePie("implementation", platform::getImplementationName);
            metrics.start();
            return metrics;
        }).onFailure(error -> {
            Failures.runCatchingNonFatal(() -> {
                if (opened[0] != null) {
                    opened[0].close();
                }
                return null;
            });
            warning.accept("Metrics could not be started: " + describe(error));
        }).getOrNull();
    }

    private static String describe(Throwable error) {
        if (error.getMessage() != null) {
            return error.getMessage();
        }
        StackTraceElement[] trace = error.getStackTrace();
        return error.getClass().getSimpleName() + " in " + (trace.length > 0 ? trace[0] : "unknown place");
    }
}
