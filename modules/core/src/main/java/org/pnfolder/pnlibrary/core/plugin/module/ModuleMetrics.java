package org.pnfolder.pnlibrary.core.plugin.module;

import org.pnfolder.pnlibrary.core.plugin.module.definition.ModuleMetricsDefinition;
import java.util.Objects;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.metrics.MetricsController;
import org.pnfolder.pnlibrary.api.metrics.MetricsService;
import org.pnfolder.pnlibrary.api.metrics.PluginMetrics;
import org.pnfolder.pnlibrary.api.metrics.error.ErrorReporter;
import org.pnfolder.pnlibrary.api.metrics.provider.MetricsProviderConfiguration;
import org.pnfolder.pnlibrary.common.lang.Failures;

/** Enables, reconfigures and closes one module's metrics session. */
public final class ModuleMetrics implements MetricsController {
    private final Object owner;
    private final MetricsService service;
    private final ErrorReporter errorReporter;
    private final Object lock = new Object();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final List<Consumer<PluginMetrics>> configurers;
    private final List<MetricsProviderConfiguration> configuredProviders;
    private volatile PluginMetrics session;

    public ModuleMetrics(@NotNull Object owner, @NotNull MetricsService service, @NotNull ModuleMetricsDefinition definition) {
        this(owner, service, definition, null);
    }

    public ModuleMetrics(@NotNull Object owner, @NotNull MetricsService service, @NotNull ModuleMetricsDefinition definition,
                         @Nullable ErrorReporter errorReporter) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.service = Objects.requireNonNull(service, "service");
        this.errorReporter = errorReporter;
        this.configurers = new ArrayList<>(definition.getConfigurers());
        this.configuredProviders = new ArrayList<>(definition.getProviders());
        if (definition.isEnabled()) {
            enable();
        }
    }

    @Override
    public boolean isEnabled() {
        return session != null;
    }

    @Override
    public boolean isClosed() {
        return closed.get();
    }

    @Override
    public @NotNull List<MetricsProviderConfiguration> getProviderConfigurations() {
        synchronized (lock) {
            return new ArrayList<>(configuredProviders);
        }
    }

    @Override
    public @Nullable ErrorReporter errorReporterOrNull() {
        PluginMetrics current = session;
        if (current == null || current.getErrorReporter() == null) {
            return null;
        }
        return errorReporter != null ? errorReporter : current.getErrorReporter();
    }

    public @Nullable ErrorReporter sessionErrorReporter() {
        PluginMetrics current = session;
        return current == null ? null : current.getErrorReporter();
    }

    @Override
    public void enable() {
        synchronized (lock) {
            checkOpen();
            if (session == null) {
                session = openSession();
            }
        }
    }

    @Override
    public void disable() {
        synchronized (lock) {
            checkOpen();
            closeSession();
        }
    }

    @Override
    public void update(@NotNull MetricsProviderConfiguration configuration) {
        synchronized (lock) {
            checkOpen();
            int index = indexOf(configuration);
            if (index >= 0 && configuredProviders.get(index).equals(configuration)) {
                return;
            }
            if (index < 0) {
                configuredProviders.add(configuration);
            } else {
                configuredProviders.set(index, configuration);
            }
            if (session != null) {
                restart();
            }
        }
    }

    @Override
    public void configure(@NotNull Consumer<PluginMetrics> configure) {
        synchronized (lock) {
            checkOpen();
            configurers.add(configure);
            if (session != null) {
                restart();
            }
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        synchronized (lock) {
            closeSession();
            configurers.clear();
        }
    }

    private int indexOf(MetricsProviderConfiguration configuration) {
        for (int i = 0; i < configuredProviders.size(); i++) {
            if (configuredProviders.get(i).getProvider() == configuration.getProvider()) {
                return i;
            }
        }
        return -1;
    }

    private void restart() {
        closeSession();
        session = openSession();
    }

    private void closeSession() {
        PluginMetrics current = session;
        if (current != null) {
            current.close();
        }
        session = null;
    }

    private PluginMetrics openSession() {
        List<MetricsProviderConfiguration> enabled = new ArrayList<>();
        for (MetricsProviderConfiguration provider : configuredProviders) {
            if (provider.isEnabled()) {
                enabled.add(provider);
            }
        }
        if (enabled.isEmpty()) {
            throw new IllegalArgumentException("At least one metrics provider must be configured and enabled");
        }
        PluginMetrics opened = service.open(owner, enabled);
        try {
            for (Consumer<PluginMetrics> configurer : configurers) {
                configurer.accept(opened);
            }
            opened.start();
            return opened;
        } catch (Throwable error) {
            Failures.runCatchingNonFatal(() -> {
                opened.close();
                return null;
            });
            throw error;
        }
    }

    private void checkOpen() {
        if (closed.get()) {
            throw new IllegalStateException("Metrics controller is closed");
        }
    }
}
