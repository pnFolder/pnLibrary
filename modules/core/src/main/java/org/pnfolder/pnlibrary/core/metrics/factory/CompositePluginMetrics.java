package org.pnfolder.pnlibrary.core.metrics.factory;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.metrics.PluginMetrics;
import org.pnfolder.pnlibrary.api.metrics.error.ErrorReporter;
import org.pnfolder.pnlibrary.api.metrics.provider.MetricsProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Fan-out session used when more than one metrics provider is enabled.
 *
 * <p>The shared chart contract is registered with every delegate. Provider-specific
 * extensions remain behind the provider's own adapter and are not silently reduced
 * to a chart that another provider cannot represent.
 */
public final class CompositePluginMetrics implements PluginMetrics {
    private final List<PluginMetrics> delegates;
    private final Runnable onClose;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final Set<MetricsProvider> providerSet;

    /** A fan-out over {@code delegates} with nothing extra to run on close. */
    public CompositePluginMetrics(@NotNull List<PluginMetrics> delegates) {
        this(delegates, () -> { });
    }

    /**
     * Creates the fan-out.
     *
     * @param delegates one session per provider; copied, so the caller cannot remove an owned backend
     * @param onClose runs after every delegate is closed
     */
    public CompositePluginMetrics(@NotNull List<PluginMetrics> delegates, @NotNull Runnable onClose) {
        this.delegates = new ArrayList<>(delegates);
        this.onClose = Objects.requireNonNull(onClose, "onClose");
        if (this.delegates.isEmpty()) {
            throw new IllegalArgumentException("At least one metrics delegate is required");
        }
        Set<MetricsProvider> providers = new LinkedHashSet<>();
        for (PluginMetrics delegate : this.delegates) {
            providers.addAll(delegate.getProviders());
        }
        this.providerSet = Collections.unmodifiableSet(providers);
    }

    @Nullable
    @Override
    public ErrorReporter getErrorReporter() {
        for (PluginMetrics delegate : delegates) {
            ErrorReporter reporter = delegate.getErrorReporter();
            if (reporter != null) {
                return reporter;
            }
        }
        return null;
    }

    @NotNull
    @Override
    public Set<MetricsProvider> getProviders() {
        return providerSet;
    }

    @NotNull
    @Override
    public PluginMetrics simplePie(@NotNull String id, @NotNull Supplier<String> value) {
        for (PluginMetrics delegate : delegates) {
            delegate.simplePie(id, value);
        }
        return this;
    }

    @NotNull
    @Override
    public PluginMetrics advancedPie(@NotNull String id, @NotNull Supplier<Map<String, Integer>> values) {
        for (PluginMetrics delegate : delegates) {
            delegate.advancedPie(id, values);
        }
        return this;
    }

    @NotNull
    @Override
    public PluginMetrics drilldownPie(@NotNull String id, @NotNull Supplier<Map<String, Map<String, Integer>>> values) {
        for (PluginMetrics delegate : delegates) {
            delegate.drilldownPie(id, values);
        }
        return this;
    }

    @NotNull
    @Override
    public PluginMetrics singleLineChart(@NotNull String id, @NotNull Supplier<Integer> value) {
        for (PluginMetrics delegate : delegates) {
            delegate.singleLineChart(id, value);
        }
        return this;
    }

    @NotNull
    @Override
    public PluginMetrics multiLineChart(@NotNull String id, @NotNull Supplier<Map<String, Integer>> values) {
        for (PluginMetrics delegate : delegates) {
            delegate.multiLineChart(id, values);
        }
        return this;
    }

    @NotNull
    @Override
    public PluginMetrics simpleBarChart(@NotNull String id, @NotNull Supplier<Map<String, Integer>> values) {
        for (PluginMetrics delegate : delegates) {
            delegate.simpleBarChart(id, values);
        }
        return this;
    }

    @NotNull
    @Override
    public PluginMetrics advancedBarChart(@NotNull String id, @NotNull Supplier<Map<String, int[]>> values) {
        for (PluginMetrics delegate : delegates) {
            delegate.advancedBarChart(id, values);
        }
        return this;
    }

    @Override
    public void start() {
        for (PluginMetrics delegate : delegates) {
            delegate.start();
        }
    }

    /** Closes the delegates in reverse order; the first failure is thrown after all are closed, later ones are suppressed. */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        Throwable failure = null;
        for (int index = delegates.size() - 1; index >= 0; index--) {
            failure = closeStep(delegates.get(index), failure);
        }
        try {
            onClose.run();
        } catch (Throwable error) {
            failure = remember(failure, error);
        }
        throwIfFailed(failure);
    }

    private static Throwable closeStep(PluginMetrics delegate, Throwable failure) {
        try {
            delegate.close();
            return failure;
        } catch (Throwable error) {
            return remember(failure, error);
        }
    }

    private static Throwable remember(Throwable failure, Throwable error) {
        if (failure == null) {
            return error;
        }
        if (error != failure) {
            failure.addSuppressed(error);
        }
        return failure;
    }

    private static void throwIfFailed(Throwable failure) {
        if (failure instanceof RuntimeException) {
            throw (RuntimeException) failure;
        }
        if (failure instanceof Error) {
            throw (Error) failure;
        }
        if (failure != null) {
            throw new IllegalStateException(failure);
        }
    }
}
