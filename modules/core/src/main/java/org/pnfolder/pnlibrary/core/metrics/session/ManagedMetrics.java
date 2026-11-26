package org.pnfolder.pnlibrary.core.metrics.session;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.metrics.PluginMetrics;
import org.pnfolder.pnlibrary.api.metrics.error.ErrorReporter;
import org.pnfolder.pnlibrary.api.metrics.provider.MetricsProvider;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Removes a delegate from its registry exactly once, even when delegate close fails. */
final class ManagedMetrics implements PluginMetrics {
    private final PluginMetrics delegate;
    private final Consumer<PluginMetrics> onClose;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    ManagedMetrics(PluginMetrics delegate, Consumer<PluginMetrics> onClose) {
        this.delegate = delegate;
        this.onClose = onClose;
    }

    @NotNull
    @Override
    public Set<MetricsProvider> getProviders() {
        return delegate.getProviders();
    }

    @Nullable
    @Override
    public ErrorReporter getErrorReporter() {
        return delegate.getErrorReporter();
    }

    @Override
    public void start() {
        delegate.start();
    }

    @NotNull
    @Override
    public PluginMetrics simplePie(@NotNull String id, @NotNull Supplier<String> value) {
        delegate.simplePie(id, value);
        return this;
    }

    @NotNull
    @Override
    public PluginMetrics advancedPie(@NotNull String id, @NotNull Supplier<Map<String, Integer>> values) {
        delegate.advancedPie(id, values);
        return this;
    }

    @NotNull
    @Override
    public PluginMetrics drilldownPie(@NotNull String id, @NotNull Supplier<Map<String, Map<String, Integer>>> values) {
        delegate.drilldownPie(id, values);
        return this;
    }

    @NotNull
    @Override
    public PluginMetrics singleLineChart(@NotNull String id, @NotNull Supplier<Integer> value) {
        delegate.singleLineChart(id, value);
        return this;
    }

    @NotNull
    @Override
    public PluginMetrics multiLineChart(@NotNull String id, @NotNull Supplier<Map<String, Integer>> values) {
        delegate.multiLineChart(id, values);
        return this;
    }

    @NotNull
    @Override
    public PluginMetrics simpleBarChart(@NotNull String id, @NotNull Supplier<Map<String, Integer>> values) {
        delegate.simpleBarChart(id, values);
        return this;
    }

    @NotNull
    @Override
    public PluginMetrics advancedBarChart(@NotNull String id, @NotNull Supplier<Map<String, int[]>> values) {
        delegate.advancedBarChart(id, values);
        return this;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            delegate.close();
        } finally {
            onClose.accept(this);
        }
    }
}
