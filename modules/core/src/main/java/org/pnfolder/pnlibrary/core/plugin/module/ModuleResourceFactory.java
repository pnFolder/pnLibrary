package org.pnfolder.pnlibrary.core.plugin.module;

import org.pnfolder.pnlibrary.core.plugin.module.definition.ModuleDefinitionBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.config.ConfigScope;
import org.pnfolder.pnlibrary.api.currency.CurrencyService;
import org.pnfolder.pnlibrary.api.currency.CurrencyStorageFactory;
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticRegistration;
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticsService;
import org.pnfolder.pnlibrary.api.downloads.DownloadRegistration;
import org.pnfolder.pnlibrary.api.events.bus.EventScope;
import org.pnfolder.pnlibrary.api.events.bus.EventService;
import org.pnfolder.pnlibrary.api.logging.LoggingService;
import org.pnfolder.pnlibrary.api.logging.PnLogger;
import org.pnfolder.pnlibrary.api.metrics.MetricsService;
import org.pnfolder.pnlibrary.api.metrics.error.ErrorReporter;
import org.pnfolder.pnlibrary.api.metrics.error.TelemetryError;
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderService;
import org.pnfolder.pnlibrary.api.plugin.ModuleId;
import org.pnfolder.pnlibrary.api.plugin.PluginId;
import org.pnfolder.pnlibrary.api.tasks.TaskScope;
import org.pnfolder.pnlibrary.api.updates.UpdateRegistration;
import org.pnfolder.pnlibrary.api.updates.UpdateService;
import org.pnfolder.pnlibrary.api.updates.product.ProductDescriptor;
import org.pnfolder.pnlibrary.config.ConfigurationRuntime;
import org.pnfolder.pnlibrary.core.cooldowns.InMemoryCooldowns;
import org.pnfolder.pnlibrary.core.downloads.DownloadService;
import org.pnfolder.pnlibrary.core.downloads.dependencies.CompositeDownloadRegistration;
import org.pnfolder.pnlibrary.core.exceptions.ResourceCleanup;
import org.pnfolder.pnlibrary.core.metrics.ErrorPipeline;
import org.pnfolder.pnlibrary.core.placeholders.PlaceholderEngine;
import org.pnfolder.pnlibrary.core.services.ServiceRegistry;
import org.pnfolder.pnlibrary.core.services.ServiceScope;
import org.pnfolder.pnlibrary.core.tasks.TaskService;
import org.pnfolder.pnlibrary.core.text.CachedComponents;
import org.pnfolder.pnlibrary.core.text.ComponentCache;
import org.pnfolder.pnlibrary.core.text.MiniText;
import org.pnfolder.pnlibrary.currency.CurrencyRuntime;

/** Creates the scoped resources of one module and rolls back already created ones if a later step fails. */
public final class ModuleResourceFactory {
    private final EventService events;
    private final TaskService tasks;
    private final ServiceRegistry services;
    private final LoggingService logging;
    private final MetricsService metrics;
    private final DiagnosticsService diagnostics;
    private final UpdateService updates;
    private final PlaceholderEngine placeholders;
    private final CurrencyRuntime currency;
    private final ConfigurationRuntime configurations;
    private final DownloadService downloadService;
    private final ComponentCache componentCache;
    private final MiniText miniText = new MiniText();

    public ModuleResourceFactory(EventService events, TaskService tasks, ServiceRegistry services, LoggingService logging,
                                 MetricsService metrics, DiagnosticsService diagnostics, UpdateService updates,
                                 PlaceholderEngine placeholders, CurrencyRuntime currency, ConfigurationRuntime configurations,
                                 @Nullable DownloadService downloadService, ComponentCache componentCache) {
        this.events = events;
        this.tasks = tasks;
        this.services = services;
        this.logging = logging;
        this.metrics = metrics;
        this.diagnostics = diagnostics;
        this.updates = updates;
        this.placeholders = placeholders;
        this.currency = currency;
        this.configurations = configurations;
        this.downloadService = downloadService;
        this.componentCache = componentCache;
    }

    public @NotNull ModuleResources create(@NotNull Object owner, @NotNull ModuleId moduleId, @NotNull PluginId moduleKey,
                                           @NotNull PluginId pluginId, @NotNull ModuleDefinitionBuilder definition,
                                           @Nullable ProductDescriptor product) {
        Draft draft = new Draft(owner, moduleId, moduleKey, pluginId, definition, product);
        try {
            return draft.build();
        } catch (Throwable error) {
            draft.rollback(error);
            throw error;
        }
    }

    /** The resources created so far, so a failed step can close exactly those. */
    private final class Draft {
        private final Object owner;
        private final ModuleId moduleId;
        private final PluginId pluginId;
        private final ModuleDefinitionBuilder definition;
        private final ProductDescriptor product;
        private final ServiceScope serviceScope;
        private TaskScope taskScope;
        private EventScope eventScope;
        private ConfigScope configScope;
        private PlaceholderService placeholderScope;
        private CurrencyService currencyScope;
        private ModuleMetrics metricsController;
        private ErrorReporter errorReporter;
        private DiagnosticRegistration diagnosticRegistration;
        private UpdateRegistration updateRegistration;
        private DownloadRegistration downloadRegistration;
        private final PluginId moduleKey;

        private Draft(Object owner, ModuleId moduleId, PluginId moduleKey, PluginId pluginId, ModuleDefinitionBuilder definition,
                      ProductDescriptor product) {
            this.owner = owner;
            this.moduleId = moduleId;
            this.moduleKey = moduleKey;
            this.pluginId = pluginId;
            this.definition = definition;
            this.product = product;
            this.serviceScope = services.ownedBy(moduleKey);
        }

        private ModuleResources build() {
            createScopes();
            PnLogger moduleLogger = logging.logger(owner, moduleId.getValue());
            createMetrics(moduleLogger);
            String diagnosticsName = ModuleDiagnosticsName.of(pluginId, moduleId);
            createRegistrations(diagnosticsName);
            return new ModuleResources(taskScope, eventScope, serviceScope, logging.logger(owner, moduleId.getValue()), configScope,
                    placeholderScope,
                    new CachedComponents(placeholderScope, componentCache, miniText, message -> moduleLogger.warning(message)),
                    new InMemoryCooldowns(), currencyScope, metricsController, errorReporter, diagnosticRegistration,
                    diagnosticsName, updateRegistration, downloadRegistration);
        }

        private void createScopes() {
            taskScope = tasks.scope(owner, moduleKey);
            eventScope = events.scope(moduleKey, pluginId, moduleId);
            configScope = configurations.scope(owner, moduleKey, pluginId, moduleId);
            placeholderScope = placeholders.scope(ModuleDiagnosticsName.of(pluginId, moduleId), definition.isPlaceholderApiEnabled());
            currencyScope = currency.scope(moduleKey, placeholderScope);
            serviceScope.register(CurrencyService.class, currencyScope);
            serviceScope.register(CurrencyStorageFactory.class, currency.getStorages());
        }

        private void createMetrics(final PnLogger moduleLogger) {
            // Resolve the live session on every capture, including after disable/restart.
            errorReporter = new ErrorPipeline(new ErrorReporter() {
                @Override
                public void capture(@NotNull TelemetryError error) {
                    ErrorReporter session = metricsController == null ? null : metricsController.sessionErrorReporter();
                    if (session != null) {
                        session.capture(error);
                    }
                }

                @Override
                public void close() {
                }
            }, failure -> moduleLogger.warning("An error report could not be sent to the error tracker", failure));
            metricsController = new ModuleMetrics(owner, metrics, definition.getMetrics(), errorReporter);
        }

        private void createRegistrations(String diagnosticsName) {
            if (definition.getDiagnosticContainer() != null) {
                diagnosticRegistration = diagnostics.register(diagnosticsName,
                        java.util.Objects.requireNonNull(definition.getDiagnosticsDirectory()), definition.getDiagnosticContainer());
            }
            if (definition.getUpdateRequest() != null) {
                updateRegistration = updates.register(owner, java.util.Objects.requireNonNull(product),
                        definition.getUpdateRequest(), definition.getDependencies());
            }
            // Retain the first registration immediately so a later failure can roll it back.
            if (downloadService != null) {
                downloadRegistration = downloadService.registerDependencies(owner, moduleId.getValue(), definition.getDependencies());
            }
            DownloadRegistration fileDownloads = null;
            if (definition.getDownloadsRequest() != null && downloadService != null) {
                fileDownloads = downloadService.register(owner, definition.getDownloadsRequest());
            }
            downloadRegistration = CompositeDownloadRegistration.combine(downloadRegistration, fileDownloads);
        }

        private void rollback(Throwable error) {
            ResourceCleanup.suppressInto(error,
                    () -> { if (downloadRegistration != null) downloadRegistration.close(); },
                    () -> { if (updateRegistration != null) updateRegistration.close(); },
                    () -> { if (diagnosticRegistration != null) diagnosticRegistration.close(); },
                    () -> { if (metricsController != null) metricsController.close(); },
                    () -> { if (errorReporter != null) errorReporter.close(); },
                    () -> { if (currencyScope != null) currencyScope.close(); },
                    () -> { if (placeholderScope != null) placeholderScope.close(); },
                    () -> { if (configScope != null) configScope.close(); },
                    () -> { if (eventScope != null) eventScope.close(); },
                    serviceScope::close,
                    () -> { if (taskScope != null) taskScope.close(); });
        }
    }
}
