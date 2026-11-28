package org.pnfolder.pnlibrary.core.plugin;

import java.util.Objects;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticsService;
import org.pnfolder.pnlibrary.api.events.bus.EventService;
import org.pnfolder.pnlibrary.api.logging.LoggingService;
import org.pnfolder.pnlibrary.api.metrics.MetricsService;
import org.pnfolder.pnlibrary.api.plugin.PluginId;
import org.pnfolder.pnlibrary.api.plugin.PluginRegistration;
import org.pnfolder.pnlibrary.api.plugin.PluginRegistry;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity;
import org.pnfolder.pnlibrary.api.updates.UpdateService;
import org.pnfolder.pnlibrary.api.updates.product.ProductDescriptor;
import org.pnfolder.pnlibrary.api.version.SemanticVersion;
import org.pnfolder.pnlibrary.config.ConfigurationRuntime;
import org.pnfolder.pnlibrary.core.commands.engine.CommandService;
import org.pnfolder.pnlibrary.core.downloads.DownloadService;
import org.pnfolder.pnlibrary.core.exceptions.CleanupStep;
import org.pnfolder.pnlibrary.core.exceptions.ResourceCleanup;
import org.pnfolder.pnlibrary.core.plugin.module.library.LibraryFetcher;
import org.pnfolder.pnlibrary.core.plugin.module.ModuleDependencyValidator;
import org.pnfolder.pnlibrary.core.plugin.module.definition.ModuleMetadataFactory;
import org.pnfolder.pnlibrary.core.plugin.module.ModulePreferences;
import org.pnfolder.pnlibrary.core.plugin.module.ModuleProductResolver;
import org.pnfolder.pnlibrary.core.plugin.module.ModuleResourceFactory;
import org.pnfolder.pnlibrary.core.plugin.module.definition.ScenarioDefinitions;
import org.pnfolder.pnlibrary.core.remote.monitor.RemotePolicyMonitor;
import org.pnfolder.pnlibrary.core.scenario.ScenarioDefinitionInfo;
import org.pnfolder.pnlibrary.core.services.ServiceRegistry;
import org.pnfolder.pnlibrary.core.tasks.TaskService;
import org.pnfolder.pnlibrary.core.text.ComponentCache;
import org.pnfolder.pnlibrary.spi.platform.logging.PlatformLogging;
import org.pnfolder.pnlibrary.spi.platform.PlatformIdentity;
import org.pnfolder.pnlibrary.spi.platform.plugin.PlatformOwnership;

/**
 * Owns plugin-scoped library services and coordinates their lifecycle.
 *
 * <p>Registration behaves transactionally: a context becomes visible only after all requested services have been created.
 * If any step fails, previously created resources are closed in reverse dependency order.
 */
public final class LoadedPlugins implements PluginRegistry {
    private final PlatformOwnership ownership;
    private final PlatformIdentity identity;
    private final DownloadService downloadService;
    private final ScenarioDefinitions scenarioDefinitions = new ScenarioDefinitions();
    private final ModuleHost host;

    public LoadedPlugins(@NotNull PlatformOwnership ownership, @NotNull PlatformIdentity identity,
                         @NotNull RemotePolicyMonitor remotePolicies, @NotNull EventService events, @NotNull TaskService tasks,
                         @NotNull ServiceRegistry services, @NotNull LoggingService logging, @NotNull MetricsService metrics,
                         @NotNull DiagnosticsService diagnostics, @NotNull UpdateService updates, @NotNull SharedFeatures shared,
                         @NotNull ConfigurationRuntime configurations, @Nullable CommandService commands,
                         @Nullable DownloadService downloadService, @Nullable String libraryVersion,
                         @NotNull ModulePreferences modulePreferences, @NotNull LibraryFetcher libraryFetcher,
                         @Nullable PlatformLogging console) {
        this.ownership = Objects.requireNonNull(ownership, "ownership");
        this.identity = Objects.requireNonNull(identity, "identity");
        this.downloadService = downloadService;
        ModuleResourceFactory resourceFactory = new ModuleResourceFactory(events, tasks, services, logging, metrics, diagnostics,
                updates, shared.getPlaceholders(), shared.getCurrency(), configurations, downloadService, new ComponentCache());
        ModuleDependencyValidator validator = new ModuleDependencyValidator(ownership, () -> downloadService != null);
        this.host = new ModuleHost(ownership, identity, modulePreferences, libraryFetcher, remotePolicies, shared, logging, diagnostics, commands,
                scenarioDefinitions, new ModuleProductResolver(ownership), new ModuleMetadataFactory(ownership, identity),
                resourceFactory, validator, console);
        registerLibraryProduct(libraryVersion);
    }

    public LoadedPlugins(@NotNull PlatformOwnership ownership, @NotNull PlatformIdentity identity,
                         @NotNull RemotePolicyMonitor remotePolicies, @NotNull EventService events, @NotNull TaskService tasks,
                         @NotNull ServiceRegistry services, @NotNull LoggingService logging, @NotNull MetricsService metrics,
                         @NotNull DiagnosticsService diagnostics, @NotNull UpdateService updates, @NotNull SharedFeatures shared,
                         @NotNull ConfigurationRuntime configurations) {
        this(ownership, identity, remotePolicies, events, tasks, services, logging, metrics, diagnostics, updates, shared,
                configurations, null, null, null, new ModulePreferences(), LibraryFetcher.THROUGH_LIBBY, null);
    }

    private void registerLibraryProduct(@Nullable String libraryVersion) {
        SemanticVersion version = libraryVersion == null ? null : SemanticVersion.tryParse(libraryVersion);
        if (version != null) {
            host.productDescriptors.put(PnLibraryIdentity.PRODUCT_ID, ProductDescriptor.library(version.toString()));
        }
    }

    /** Every named action, requirement and function of every module. */
    public @NotNull List<ScenarioDefinitionInfo> scenarioDefinitionInfos() {
        return scenarioDefinitions.infos();
    }

    @Override
    public @NotNull PluginRegistration register(@NotNull Object owner) {
        synchronized (host.plugins) {
            if (host.closed.get()) {
                throw new IllegalStateException("PluginRegistry is closed");
            }
            if (!ownership.acceptsOwner(owner)) {
                throw new IllegalArgumentException(
                        "Object " + owner.getClass().getName() + " is not a supported owner for " + identity.getType().getId());
            }
            if (host.plugins.containsKey(owner)) {
                throw new IllegalArgumentException("This platform plugin is already registered");
            }
            Map<String, String> details = ownership.ownerDetails(owner);
            String nativeId = details.get("id") != null ? details.get("id") : details.get("name");
            if (nativeId == null) {
                throw new IllegalStateException("The platform did not expose a plugin ID for " + owner.getClass().getName());
            }
            PluginId id = PluginId.of(nativeId);
            if (id.getValue().length() > 64 || id.getValue().indexOf('@') >= 0) {
                throw new IllegalArgumentException("Plugin ID '" + id + "' must be up to 64 characters and not contain '@': it separates plugin and module");
            }
            for (LoadedPlugin other : host.plugins.values()) {
                if (other.getNativeId().equals(id)) {
                    throw new IllegalArgumentException("Plugin '" + id + "' is already registered; unregister the old instance first");
                }
            }
            LoadedPlugin plugin = new LoadedPlugin(host, owner, id);
            host.plugins.put(owner, plugin);
            return plugin;
        }
    }

    @Override
    public @Nullable PluginRegistration get(@NotNull Object owner) {
        synchronized (host.plugins) {
            return host.plugins.get(owner);
        }
    }

    @Override
    public void unregister(@NotNull Object owner) {
        LoadedPlugin plugin = host.detachPlugin(owner);
        if (plugin != null) {
            plugin.closeInternal();
        }
    }

    @Override
    public @NotNull List<PluginRegistration> all() {
        synchronized (host.plugins) {
            return Collections.unmodifiableList(new ArrayList<>(host.plugins.values()));
        }
    }

    @Override
    public void close() {
        if (!host.closed.compareAndSet(false, true)) {
            return;
        }
        List<LoadedPlugin> current;
        synchronized (host.plugins) {
            current = new ArrayList<>(host.plugins.values());
            host.plugins.clear();
            host.productDescriptors.clear();
        }
        List<CleanupStep> steps = new ArrayList<>();
        for (final LoadedPlugin plugin : current) {
            steps.add(plugin::closeInternal);
        }
        steps.add(() -> {
            if (downloadService != null) {
                downloadService.close();
            }
        });
        ResourceCleanup.closeAll(steps);
    }
}
