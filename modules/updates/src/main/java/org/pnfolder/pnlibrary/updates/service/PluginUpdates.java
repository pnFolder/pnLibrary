package org.pnfolder.pnlibrary.updates.service;

import org.pnfolder.pnlibrary.updates.service.registration.UpdateRegistrationStore;
import org.pnfolder.pnlibrary.updates.service.registration.ValidatedRegistration;
import org.pnfolder.pnlibrary.updates.service.registration.RegistrationValidator;
import org.pnfolder.pnlibrary.updates.service.registration.RegisteredUpdate;
import org.pnfolder.pnlibrary.updates.service.registration.RegisteredProducts;
import org.pnfolder.pnlibrary.updates.service.plan.PlanOperations;
import java.util.Objects;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CompletionStage;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.dependencies.PluginDependency;
import org.pnfolder.pnlibrary.api.logging.LogLevel;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity;
import org.pnfolder.pnlibrary.api.updates.UpdateBackups;
import org.pnfolder.pnlibrary.api.updates.UpdateHistory;
import org.pnfolder.pnlibrary.api.updates.UpdatePlans;
import org.pnfolder.pnlibrary.api.updates.UpdateProducts;
import org.pnfolder.pnlibrary.api.updates.UpdateRegistration;
import org.pnfolder.pnlibrary.api.updates.UpdateService;
import org.pnfolder.pnlibrary.api.updates.UpdateSettings;
import org.pnfolder.pnlibrary.api.updates.product.ProductDescriptor;
import org.pnfolder.pnlibrary.api.updates.product.ProductId;
import org.pnfolder.pnlibrary.api.updates.request.PluginUpdateRequest;
import org.pnfolder.pnlibrary.api.updates.state.UpdateActor;
import org.pnfolder.pnlibrary.api.updates.state.UpdatePlanSnapshot;
import org.pnfolder.pnlibrary.api.updates.state.UpdateSnapshot;
import org.pnfolder.pnlibrary.console.ConsoleTheme;
import org.pnfolder.pnlibrary.spi.platform.PlatformIdentity;
import org.pnfolder.pnlibrary.spi.platform.PlatformLogging;
import org.pnfolder.pnlibrary.spi.platform.PlatformOwnerMetadata;
import org.pnfolder.pnlibrary.spi.platform.PlatformPluginInventory;
import org.pnfolder.pnlibrary.spi.platform.PlatformReadiness;
import org.pnfolder.pnlibrary.updates.databases.DatabaseBackups;
import org.pnfolder.pnlibrary.updates.databases.DatabaseSources;
import org.pnfolder.pnlibrary.updates.engine.FreezeStore;
import org.pnfolder.pnlibrary.updates.engine.ResolutionResult;
import org.pnfolder.pnlibrary.updates.engine.UpdatePreferencesStore;
import org.pnfolder.pnlibrary.updates.engine.catalog.RefreshMode;
import org.pnfolder.pnlibrary.updates.install.UpdateGraphResolver;
import org.pnfolder.pnlibrary.updates.install.UpdateInstaller;
import org.pnfolder.pnlibrary.updates.paths.LibraryPaths;
import org.pnfolder.pnlibrary.updates.policy.UpdateConfiguration;
import org.pnfolder.pnlibrary.updates.policy.UpdateDefaults;
import org.pnfolder.pnlibrary.updates.policy.UpdatePolicyOverrides;
import org.pnfolder.pnlibrary.updates.policy.UpdateTiming;

/** One catalog → resolver → verifier → transaction pipeline for every component. */
public final class PluginUpdates implements UpdateService, AutoCloseable {
    private final PlatformLogging logging;
    private final PlatformIdentity identity;
    private final LibraryPaths paths;
    private final ConsoleTheme consoleTheme;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final UpdateRegistrationStore registrations = new UpdateRegistrationStore();
    private final RegistrationValidator registrationValidator;
    private final UpdateRuntimeResources resources;
    private final UpdatePolicyOverrides overrides;
    private final DatabaseSources databaseSources = new DatabaseSources();
    private final UpdateInstaller installer;
    private final UpdateGraphResolver graphResolver;
    private final UpdateOrchestratorRuntime runtime;
    private final UpdateProducts products = new RegisteredProducts(registrations);
    private final UpdatePlans plans;
    private final UpdateHistory history;
    private final UpdateSettings settings;
    private final UpdateBackups backups;

    /** Creates the update service with the default check interval and timeouts. */
    public PluginUpdates(@NotNull PlatformLogging logging, @NotNull PlatformOwnerMetadata ownership,
                         @NotNull PlatformPluginInventory inventory, @NotNull PlatformIdentity identity,
                         @NotNull PlatformReadiness readiness, @NotNull LibraryPaths paths,
                         @NotNull ConsoleTheme consoleTheme) throws IOException {
        this(logging, ownership, inventory, identity, readiness, paths, consoleTheme, new UpdateTiming());
    }

    /** Creates the update service with the check interval and timeouts of {@code timing}. */
    public PluginUpdates(@NotNull PlatformLogging logging, @NotNull PlatformOwnerMetadata ownership,
                         @NotNull PlatformPluginInventory inventory, @NotNull PlatformIdentity identity,
                         @NotNull PlatformReadiness readiness, @NotNull LibraryPaths paths,
                         @NotNull ConsoleTheme consoleTheme, @NotNull UpdateTiming timing) throws IOException {
        this.logging = Objects.requireNonNull(logging, "logging");
        this.identity = Objects.requireNonNull(identity, "identity");
        this.paths = Objects.requireNonNull(paths, "paths");
        this.consoleTheme = Objects.requireNonNull(consoleTheme, "consoleTheme");
        UpdateConfiguration configuration = new UpdateConfiguration(
                new UpdateConfiguration.Checks(timing.getCheckInterval()),
                new UpdateConfiguration.Downloads(UpdateDefaults.ALLOW_MANAGED_PLUGINS, timing.getConnectTimeout(),
                        timing.getReadTimeout(), UpdateDefaults.MAX_ARTIFACT_BYTES),
                new UpdateConfiguration.Installation(), new UpdateConfiguration.LibraryPolicy());
        this.registrationValidator = new RegistrationValidator(ownership, identity);
        retireOldSettingsFile();
        this.resources = new UpdateRuntimeResources(paths.getCatalog(), configuration);
        UpdatePreferencesStore preferences = new UpdatePreferencesStore(paths.getPreferences(), this::warn);
        this.overrides = new UpdatePolicyOverrides(preferences, this::warn);
        this.installer = new UpdateInstaller(identity, paths, configuration, resources.getDownloadHttp(),
                new DatabaseBackups(paths.getDatabases(), databaseSources),
                product -> overrides.javaBuild(product.getValue()), this::installedJava, this::warn);
        FreezeStore freezes = new FreezeStore(preferences);
        this.graphResolver = new UpdateGraphResolver(inventory, identity, configuration, resources.getCatalogGateway(),
                freezes, overrides, (message, error) -> logging.log(this, LogLevel.ERROR, message, error));
        this.runtime = new UpdateOrchestratorRuntime(configuration, paths, logging, readiness, installer,
                registrations::snapshot, this::resolveGraph, this::stageGraph, this::announce,
                snapshot -> automaticUpdatesHealthy());
        this.plans = new PlanOperations(runtime);
        this.history = new HistoryReader(runtime);
        this.settings = new SettingsChanges(this, logging, registrations, overrides, graphResolver, freezes, runtime,
                closed::get);
        this.backups = new BackupOperations(this, logging, installer, runtime);
    }

    private void warn(String message) {
        logging.log(this, LogLevel.WARNING, message);
    }

    private Integer installedJava(ProductId product) {
        RegisteredUpdate registration = registrations.find(product.getValue());
        return registration == null ? null : registration.getDescriptor().getMinimumJava();
    }

    private boolean automaticUpdatesHealthy() {
        Set<ProductId> ids = new HashSet<>();
        for (RegisteredUpdate registration : registrations.snapshot()) {
            ids.add(registration.getDescriptor().getId());
        }
        try {
            return installer.automaticUpdatesHealthy(ids);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    /** The settings file of older versions no longer does anything; it is kept aside so nothing is lost. */
    private void retireOldSettingsFile() throws IOException {
        Path old = paths.getPluginFolder().resolve("updates.yml");
        if (!Files.isRegularFile(old)) {
            return;
        }
        Path kept = paths.getPluginFolder().resolve("updates.yml.old");
        Files.move(old, kept, StandardCopyOption.REPLACE_EXISTING);
        logging.log(this, LogLevel.WARNING, "updates.yml больше не используется: обновления настраиваются командой "
                + "/pn update и в меню. Файл сохранён как " + kept.getFileName() + ".");
    }

    /** Where plugins say their databases are; the configuration service fills it as files are registered. */
    @NotNull
    public DatabaseSources getDatabaseSources() {
        return databaseSources;
    }

    @NotNull
    @Override
    public UpdateProducts getProducts() {
        return products;
    }

    @NotNull
    @Override
    public UpdatePlans getPlans() {
        return plans;
    }

    @NotNull
    @Override
    public UpdateHistory getHistory() {
        return history;
    }

    @NotNull
    @Override
    public UpdateSettings getSettings() {
        return settings;
    }

    @NotNull
    @Override
    public UpdateBackups getBackups() {
        return backups;
    }

    @NotNull
    @Override
    public UpdateRegistration register(@NotNull Object owner, @NotNull ProductDescriptor product,
                                       @NotNull PluginUpdateRequest request, @NotNull List<PluginDependency> dependencies) {
        requireOpen();
        ValidatedRegistration validated = registrationValidator.validate(owner, product, request);
        UpdateOrchestrator orchestrator = runtime.getOrchestrator();
        RegisteredUpdate registration = new RegisteredUpdate(product, request, validated.getArtifact(), validated.getJar(),
                validated.getPluginsDirectory().resolve("update"),
                () -> graphResolver.channelFor(product.getId(), request.getChannel()),
                orchestrator::checkNow, snapshot -> orchestrator.stage(snapshot.getId()), this::removeRegistration,
                () -> true, identity.getType(),
                () -> overrides.javaBuild(product.getId().getValue()));
        requireOpen();
        registrations.add(registration);
        orchestrator.registrationsChanged();
        return registration;
    }

    private void requireOpen() {
        if (closed.get()) {
            throw new IllegalStateException("Update service is closed");
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        registrations.close();
        runtime.close();
        resources.close();
    }

    private ResolutionResult resolveGraph(RefreshMode refreshMode) {
        return graphResolver.resolve(registrations.snapshot(), refreshMode);
    }

    /** Prepares a library-only release requested by an administrator. */
    @NotNull
    public CompletionStage<UpdatePlanSnapshot> forceLibrary(@NotNull UpdateActor actor) {
        return runtime.getOrchestrator().stageLibrary(() -> graphResolver.forceLibrary(registrations.snapshot()), actor);
    }

    private void stageGraph(UpdatePlanSnapshot snapshot) {
        try {
            installer.stage(snapshot, registrations.snapshot());
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    private void announce(UpdatePlanSnapshot snapshot) {
        List<UpdateSnapshot> snapshots = new ArrayList<>();
        for (RegisteredUpdate registration : registrations.snapshot()) {
            snapshots.add(registration.getSnapshot());
        }
        for (String line : UpdateAnnouncementRenderer.render(snapshot, consoleTheme, snapshots)) {
            logging.console(this, line);
        }
    }

    private void removeRegistration(RegisteredUpdate registration) {
        registrations.remove(registration);
        runtime.getOrchestrator().registrationsChanged();
    }
}
