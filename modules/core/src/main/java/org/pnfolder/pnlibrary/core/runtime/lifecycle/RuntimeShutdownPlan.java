package org.pnfolder.pnlibrary.core.runtime.lifecycle;

import java.util.function.Supplier;
import org.pnfolder.pnlibrary.core.exceptions.CleanupStep;

/** Every step runs even when an earlier one fails; all failures are rethrown together by {@link RuntimeShutdown}. */
public final class RuntimeShutdownPlan {
    private final AutoCloseable plugins;
    private final AutoCloseable commands;
    private final AutoCloseable players;
    private final AutoCloseable platforms;
    private final AutoCloseable currency;
    private final AutoCloseable expansions;
    private final AutoCloseable libraryExpansions;
    private final AutoCloseable configurations;
    private final Supplier<AutoCloseable> libraryMetrics;
    private final AutoCloseable metrics;
    private final AutoCloseable updates;
    private final AutoCloseable events;
    private final AutoCloseable services;
    private final AutoCloseable tasks;
    private final AutoCloseable support;
    private final CleanupStep releaseProvider;
    private final CleanupStep closePlatform;
    private final CleanupStep onClose;

    public RuntimeShutdownPlan(AutoCloseable plugins, AutoCloseable commands, AutoCloseable players, AutoCloseable platforms,
                               AutoCloseable currency, AutoCloseable expansions, AutoCloseable libraryExpansions,
                               AutoCloseable configurations, Supplier<AutoCloseable> libraryMetrics, AutoCloseable metrics,
                               AutoCloseable updates, AutoCloseable events, AutoCloseable services, AutoCloseable tasks,
                               AutoCloseable support, CleanupStep releaseProvider, CleanupStep closePlatform, CleanupStep onClose) {
        this.plugins = plugins;
        this.commands = commands;
        this.players = players;
        this.platforms = platforms;
        this.currency = currency;
        this.expansions = expansions;
        this.libraryExpansions = libraryExpansions;
        this.configurations = configurations;
        this.libraryMetrics = libraryMetrics;
        this.metrics = metrics;
        this.updates = updates;
        this.events = events;
        this.services = services;
        this.tasks = tasks;
        this.support = support;
        this.releaseProvider = releaseProvider;
        this.closePlatform = closePlatform;
        this.onClose = onClose;
    }

    public void run() {
        RuntimeShutdown shutdown = new RuntimeShutdown();
        for (final AutoCloseable step : new AutoCloseable[] {plugins, commands, players, platforms, currency, expansions,
            libraryExpansions, configurations}) {
            shutdown.add(step::close);
        }
        shutdown.add(() -> closeIfPresent(libraryMetrics.get()));
        for (final AutoCloseable step : new AutoCloseable[] {metrics, updates, events, services, tasks, support}) {
            shutdown.add(step::close);
        }
        shutdown.add(releaseProvider);
        shutdown.add(closePlatform);
        shutdown.add(onClose);
        shutdown.run();
    }

    private static void closeIfPresent(AutoCloseable closeable) throws Exception {
        if (closeable == null) {
            return;
        }
        closeable.close();
    }
}
