package org.pnfolder.pnlibrary.api.runtime;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.audience.Audiences;
import org.pnfolder.pnlibrary.api.config.ConfigurationService;
import org.pnfolder.pnlibrary.api.diagnostics.DebugRequest;
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticsService;
import org.pnfolder.pnlibrary.api.diagnostics.report.DiagnosticReport;
import org.pnfolder.pnlibrary.api.events.bus.EventService;
import org.pnfolder.pnlibrary.api.expansions.ExpansionManager;
import org.pnfolder.pnlibrary.api.logging.LoggingService;
import org.pnfolder.pnlibrary.api.metrics.MetricsService;
import org.pnfolder.pnlibrary.api.network.NetworkProxy;
import org.pnfolder.pnlibrary.api.observability.ObservabilityService;
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderAdapterRegistry;
import org.pnfolder.pnlibrary.api.platform.PlatformProvider;
import org.pnfolder.pnlibrary.api.plugin.PluginRegistry;
import org.pnfolder.pnlibrary.api.runtime.group.Monitoring;
import org.pnfolder.pnlibrary.api.runtime.group.Registrations;
import org.pnfolder.pnlibrary.api.runtime.group.RuntimeInfo;
import org.pnfolder.pnlibrary.api.services.ServiceManager;
import org.pnfolder.pnlibrary.api.updates.UpdateService;

import java.io.Closeable;

/**
 * Main pnLibrary facade shared by all plugins in one server process.
 *
 * <p>Consumers obtain this interface through {@link PnLibraryProvider}. They must not depend on {@code core}
 * implementation classes, so the internals can evolve without breaking installed plugins.
 */
@ApiStatus.NonExtendable
public interface PnLibrary extends Closeable {
    /** Version of the installed runtime. */
    @NotNull
    String getVersion();

    /** Returns {@code true} when this runtime is not older than {@code minimumVersion}. */
    boolean isAtLeastVersion(@NotNull String minimumVersion);

    /** Native platform plugin instance that owns this runtime. */
    @NotNull
    Object getOwner();

    /** Validated configuration of the installed runtime. */
    @NotNull
    PnLibraryConfig getConfiguration();

    /** Registry for diagnostic data contributed by consumer plugins. */
    @NotNull
    DiagnosticsService getDiagnostics();

    /** Observations, component status and support reports. */
    @NotNull
    ObservabilityService getObservability();

    /** Managed bStats sessions; every plugin supplies its own project ID. */
    @NotNull
    MetricsService getMetrics();

    /** Native logging and formatted lifecycle summaries. */
    @NotNull
    LoggingService getLogging();

    /** Typed YAML configuration factory for Java and Kotlin plugins. */
    @NotNull
    ConfigurationService getConfigurations();

    /** Update checks and staged downloads for registered plugins. */
    @NotNull
    UpdateService getUpdates();

    /** Cross-platform players, the console, native senders and groups of them. */
    @NotNull
    Audiences getAudiences();

    /** Typed process-wide services with plugin-owned registrations. */
    @NotNull
    ServiceManager getServices();

    /** Shared features, such as localization, that the library enables and disables as units. */
    @NotNull
    ExpansionManager getExpansions();

    /** Mode-aware cross-platform event bus with plugin-ID-bound subscriptions. */
    @NotNull
    EventService getEvents();

    /** Global registry and high-level entry point for consumer plugins. */
    @NotNull
    PluginRegistry getPlugins();

    /** Type-safe native API for the active Bukkit, Bungee, or Velocity runtime. */
    @NotNull
    PlatformProvider getPlatforms();

    /** The proxy layer; null when running on a game server instead of a proxy. */
    @Nullable
    NetworkProxy getProxy();

    /**
     * Runtime-detected placeholder bridges such as PlaceholderAPI.
     *
     * <p>Consumer plugins normally use their plugin context instead of registering adapters here.
     */
    @NotNull
    PlaceholderAdapterRegistry getPlaceholderAdapters();

    /** Logs, metrics, diagnostics and reports in one place. */
    @NotNull
    default Monitoring getObserve() {
        final PnLibrary library = this;
        return new Monitoring() {
            @NotNull
            @Override
            public LoggingService getLogging() {
                return library.getLogging();
            }

            @NotNull
            @Override
            public MetricsService getMetrics() {
                return library.getMetrics();
            }

            @NotNull
            @Override
            public DiagnosticsService getDiagnostics() {
                return library.getDiagnostics();
            }

            @NotNull
            @Override
            public ObservabilityService getObservability() {
                return library.getObservability();
            }

            @NotNull
            @Override
            public DiagnosticReport report(@NotNull DebugRequest request) {
                return library.createDiagnosticReport(request);
            }
        };
    }

    /** Plugins, services, expansions and placeholder bridges in one place. */
    @NotNull
    default Registrations getRegistry() {
        final PnLibrary library = this;
        return new Registrations() {
            @NotNull
            @Override
            public PluginRegistry getPlugins() {
                return library.getPlugins();
            }

            @NotNull
            @Override
            public ServiceManager getServices() {
                return library.getServices();
            }

            @NotNull
            @Override
            public ExpansionManager getExpansions() {
                return library.getExpansions();
            }

            @NotNull
            @Override
            public PlaceholderAdapterRegistry getPlaceholderAdapters() {
                return library.getPlaceholderAdapters();
            }
        };
    }

    /** Version, settings, platform and proxy in one place. */
    @NotNull
    default RuntimeInfo getRuntime() {
        final PnLibrary library = this;
        return new RuntimeInfo() {
            @NotNull
            @Override
            public String getVersion() {
                return library.getVersion();
            }

            @Override
            public boolean isAtLeastVersion(@NotNull String minimumVersion) {
                return library.isAtLeastVersion(minimumVersion);
            }

            @NotNull
            @Override
            public Object getOwner() {
                return library.getOwner();
            }

            @NotNull
            @Override
            public PnLibraryConfig getConfiguration() {
                return library.getConfiguration();
            }

            @NotNull
            @Override
            public PlatformProvider getPlatforms() {
                return library.getPlatforms();
            }

            @Nullable
            @Override
            public NetworkProxy getProxy() {
                return library.getProxy();
            }

            @Override
            public boolean isClosed() {
                return library.isClosed();
            }
        };
    }

    /** Builds a diagnostic report from an already validated request. */
    @NotNull
    DiagnosticReport createDiagnosticReport(@NotNull DebugRequest request);

    /** Whether {@link #close} has begun and the runtime no longer accepts normal work. */
    boolean isClosed();

    /**
     * Stops owned plugins and services, releases resources, and clears {@link PnLibraryProvider}.
     *
     * <p>Closing the runtime more than once has no additional effect.
     */
    @Override
    void close();
}
