package org.pnfolder.pnlibrary.core.metrics.factory;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.metrics.PluginMetrics;

import java.util.function.BiFunction;

/**
 * Metrics factory for platforms whose metrics owner is a native plugin object of type {@code P}.
 *
 * <p>The owner is validated once here; platforms only say how to open a bStats or FastStats session for a plugin.
 */
public class NativePluginMetricsFactory<P> extends ProviderMetricsFactory {
    private final Class<P> ownerType;
    private final String platformName;
    private final BiFunction<P, Integer, PluginMetrics> bStats;
    private final BiFunction<P, String, PluginMetrics> fastStats;

    /**
     * Creates the factory.
     *
     * @param ownerType native plugin class every owner must be an instance of
     * @param platformName platform name used in the validation message, for example {@code Bukkit}
     * @param bStats opens a bStats session for a plugin and project identifier
     * @param fastStats opens a FastStats session for a plugin and project token
     */
    public NativePluginMetricsFactory(@NotNull Class<P> ownerType, @NotNull String platformName,
                                      @NotNull BiFunction<P, Integer, PluginMetrics> bStats,
                                      @NotNull BiFunction<P, String, PluginMetrics> fastStats) {
        this.ownerType = ownerType;
        this.platformName = platformName;
        this.bStats = bStats;
        this.fastStats = fastStats;
    }

    /** @throws IllegalArgumentException when {@code owner} is not a native plugin of this platform */
    @NotNull
    @Override
    protected final PluginMetrics openBStats(@NotNull Object owner, int projectId) {
        return bStats.apply(plugin(owner), projectId);
    }

    /** @throws IllegalArgumentException when {@code owner} is not a native plugin of this platform */
    @NotNull
    @Override
    protected final PluginMetrics openFastStats(@NotNull Object owner, @NotNull String token) {
        return fastStats.apply(plugin(owner), token);
    }

    private P plugin(Object owner) {
        if (!ownerType.isInstance(owner)) {
            throw new IllegalArgumentException(platformName + " metrics owner must be a " + platformName
                    + " plugin; received " + owner.getClass().getName());
        }
        return ownerType.cast(owner);
    }
}
