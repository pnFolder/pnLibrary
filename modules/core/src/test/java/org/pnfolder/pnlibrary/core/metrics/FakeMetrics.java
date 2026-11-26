package org.pnfolder.pnlibrary.core.metrics;

import java.util.Locale;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.metrics.PluginMetrics;
import org.pnfolder.pnlibrary.api.metrics.provider.MetricsProvider;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/** A metrics session for tests: one provider, charts ignored, every call optionally logged to {@code calls}. */
class FakeMetrics implements PluginMetrics {
    private final MetricsProvider provider;
    private final List<String> calls;
    private final Runnable onClose;

    FakeMetrics(MetricsProvider provider, List<String> calls, Runnable onClose) {
        this.provider = provider;
        this.calls = calls;
        this.onClose = onClose;
    }

    static FakeMetrics closing(Runnable onClose) {
        return new FakeMetrics(MetricsProvider.BSTATS, null, onClose);
    }

    @NotNull
    @Override
    public Set<MetricsProvider> getProviders() {
        return Collections.singleton(provider);
    }

    @NotNull
    @Override
    public PluginMetrics simplePie(@NotNull String id, @NotNull Supplier<String> value) {
        if (calls != null) {
            calls.add(provider.name().toLowerCase(Locale.ROOT) + ":chart");
        }
        return this;
    }

    @NotNull
    @Override
    public PluginMetrics advancedPie(@NotNull String id, @NotNull Supplier<Map<String, Integer>> values) {
        return this;
    }

    @NotNull
    @Override
    public PluginMetrics drilldownPie(@NotNull String id, @NotNull Supplier<Map<String, Map<String, Integer>>> values) {
        return this;
    }

    @NotNull
    @Override
    public PluginMetrics singleLineChart(@NotNull String id, @NotNull Supplier<Integer> value) {
        return this;
    }

    @NotNull
    @Override
    public PluginMetrics multiLineChart(@NotNull String id, @NotNull Supplier<Map<String, Integer>> values) {
        return this;
    }

    @NotNull
    @Override
    public PluginMetrics simpleBarChart(@NotNull String id, @NotNull Supplier<Map<String, Integer>> values) {
        return this;
    }

    @NotNull
    @Override
    public PluginMetrics advancedBarChart(@NotNull String id, @NotNull Supplier<Map<String, int[]>> values) {
        return this;
    }

    @Override
    public void close() {
        if (calls != null) {
            calls.add(provider.name().toLowerCase(Locale.ROOT) + ":close");
        }
        onClose.run();
    }
}
