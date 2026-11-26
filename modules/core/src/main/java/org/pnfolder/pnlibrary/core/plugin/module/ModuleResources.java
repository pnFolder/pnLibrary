package org.pnfolder.pnlibrary.core.plugin.module;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.config.ConfigScope;
import org.pnfolder.pnlibrary.api.cooldowns.CooldownService;
import org.pnfolder.pnlibrary.api.currency.CurrencyService;
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticRegistration;
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticsService;
import org.pnfolder.pnlibrary.api.downloads.DownloadRegistration;
import org.pnfolder.pnlibrary.api.events.bus.EventScope;
import org.pnfolder.pnlibrary.api.logging.PnLogger;
import org.pnfolder.pnlibrary.api.metrics.MetricsController;
import org.pnfolder.pnlibrary.api.metrics.error.ErrorReporter;
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderService;
import org.pnfolder.pnlibrary.api.tasks.TaskScope;
import org.pnfolder.pnlibrary.api.text.ComponentService;
import org.pnfolder.pnlibrary.api.updates.UpdateRegistration;
import org.pnfolder.pnlibrary.core.exceptions.ResourceCleanup;
import org.pnfolder.pnlibrary.core.services.ServiceScope;

/** Per-module resources (tasks, events, services, config, placeholders and so on) closed together with the module. */
public final class ModuleResources {
    private final TaskScope tasks;
    private final EventScope events;
    private final ServiceScope services;
    private final PnLogger logger;
    private final ConfigScope configs;
    private final PlaceholderService placeholders;
    private final ComponentService components;
    private final CooldownService cooldowns;
    private final CurrencyService currency;
    private final MetricsController metrics;
    private final ErrorReporter errors;
    private final DiagnosticRegistration diagnostics;
    private final String diagnosticsName;
    private final UpdateRegistration updates;
    private final DownloadRegistration downloads;

    public ModuleResources(TaskScope tasks, EventScope events, ServiceScope services, PnLogger logger, ConfigScope configs,
                           PlaceholderService placeholders, ComponentService components, CooldownService cooldowns,
                           CurrencyService currency, MetricsController metrics, ErrorReporter errors,
                           @Nullable DiagnosticRegistration diagnostics, String diagnosticsName,
                           @Nullable UpdateRegistration updates, @Nullable DownloadRegistration downloads) {
        this.tasks = tasks;
        this.events = events;
        this.services = services;
        this.logger = logger;
        this.configs = configs;
        this.placeholders = placeholders;
        this.components = components;
        this.cooldowns = cooldowns;
        this.currency = currency;
        this.metrics = metrics;
        this.errors = errors;
        this.diagnostics = diagnostics;
        this.diagnosticsName = diagnosticsName;
        this.updates = updates;
        this.downloads = downloads;
    }

    public @NotNull TaskScope getTasks() {
        return tasks;
    }

    public @NotNull EventScope getEvents() {
        return events;
    }

    public @NotNull ServiceScope getServices() {
        return services;
    }

    public @NotNull PnLogger getLogger() {
        return logger;
    }

    public @NotNull ConfigScope getConfigs() {
        return configs;
    }

    public @NotNull PlaceholderService getPlaceholders() {
        return placeholders;
    }

    public @NotNull ComponentService getComponents() {
        return components;
    }

    public @NotNull CooldownService getCooldowns() {
        return cooldowns;
    }

    public @NotNull CurrencyService getCurrency() {
        return currency;
    }

    public @NotNull MetricsController getMetrics() {
        return metrics;
    }

    public @NotNull ErrorReporter getErrors() {
        return errors;
    }

    public @Nullable DiagnosticRegistration getDiagnostics() {
        return diagnostics;
    }

    public @Nullable UpdateRegistration getUpdates() {
        return updates;
    }

    public @Nullable DownloadRegistration getDownloads() {
        return downloads;
    }

    public void close(@NotNull DiagnosticsService diagnosticsService) {
        ResourceCleanup.closeAll(
                () -> { if (downloads != null) downloads.close(); },
                () -> { if (updates != null) updates.close(); },
                () -> { if (diagnostics != null) diagnostics.close(); },
                () -> diagnosticsService.clearPlugin(diagnosticsName),
                metrics::close,
                errors::close,
                currency::close,
                cooldowns::close,
                placeholders::close,
                configs::close,
                services::close,
                events::close,
                tasks::close);
    }
}
