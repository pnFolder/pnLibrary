package org.pnfolder.pnlibrary.api.metrics;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.metrics.error.ErrorReporter;
import org.pnfolder.pnlibrary.api.metrics.provider.MetricsProvider;

import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Live plugin metrics session with fluent registration of standard charts.
 *
 * <p>Suppliers are evaluated by the platform metrics implementation, potentially after registration and on a
 * background thread. They should therefore be thread-safe, non-blocking, and free of destructive side effects. Closing
 * a managed session is safe to repeat and prevents its registry from retaining the session.
 */
public interface PluginMetrics extends AutoCloseable {
    /** Providers represented by this session; more than one when several are enabled. */
    @NotNull
    Set<MetricsProvider> getProviders();

    /** Error reporter owned by this session; absent when no provider supports error tracking. */
    @Nullable
    default ErrorReporter getErrorReporter() {
        return null;
    }

    /** Starts provider submission after all configured charts and services are registered. */
    default void start() {
    }

    /** Registers a single categorical string value; {@code null} omits the current sample. */
    @NotNull
    PluginMetrics simplePie(@NotNull String id, @NotNull Supplier<String> value);

    /** Registers category-to-count values for one pie chart. */
    @NotNull
    PluginMetrics advancedPie(@NotNull String id, @NotNull Supplier<Map<String, Integer>> values);

    /** Registers two-level category-to-count values for a drilldown chart. */
    @NotNull
    PluginMetrics drilldownPie(@NotNull String id, @NotNull Supplier<Map<String, Map<String, Integer>>> values);

    /** Registers one integer sample for a line chart. */
    @NotNull
    PluginMetrics singleLineChart(@NotNull String id, @NotNull Supplier<Integer> value);

    /** Registers named integer samples for a multiline chart. */
    @NotNull
    PluginMetrics multiLineChart(@NotNull String id, @NotNull Supplier<Map<String, Integer>> values);

    /** Registers named integer bars for a simple bar chart. */
    @NotNull
    PluginMetrics simpleBarChart(@NotNull String id, @NotNull Supplier<Map<String, Integer>> values);

    /** Registers named arrays of integer bars for an advanced bar chart. */
    @NotNull
    PluginMetrics advancedBarChart(@NotNull String id, @NotNull Supplier<Map<String, int[]>> values);

    /** Stops this metrics session and releases its registry ownership. */
    @Override
    void close();
}
