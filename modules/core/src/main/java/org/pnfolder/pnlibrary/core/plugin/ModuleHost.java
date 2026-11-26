package org.pnfolder.pnlibrary.core.plugin;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticsService;
import org.pnfolder.pnlibrary.api.logging.LoggingService;
import org.pnfolder.pnlibrary.api.updates.product.ProductDescriptor;
import org.pnfolder.pnlibrary.core.commands.engine.CommandService;
import org.pnfolder.pnlibrary.core.plugin.module.library.LibraryFetcher;
import org.pnfolder.pnlibrary.core.plugin.module.ModuleDependencyValidator;
import org.pnfolder.pnlibrary.core.plugin.module.definition.ModuleMetadataFactory;
import org.pnfolder.pnlibrary.core.plugin.module.ModulePreferences;
import org.pnfolder.pnlibrary.core.plugin.module.ModuleProductResolver;
import org.pnfolder.pnlibrary.core.plugin.module.ModuleResourceFactory;
import org.pnfolder.pnlibrary.core.plugin.module.definition.ScenarioDefinitions;
import org.pnfolder.pnlibrary.core.remote.monitor.RemotePolicyMonitor;
import org.pnfolder.pnlibrary.spi.platform.PlatformIdentity;

/**
 * What {@link LoadedPlugin} and {@link LoadedModule} share with the registry that created them: the collaborators the
 * registry was built from and the state it guards. {@code plugins} is also the lock of every registration and removal.
 */
final class ModuleHost {
    final PlatformIdentity identity;
    final ModulePreferences preferences;
    final LibraryFetcher libraryFetcher;
    final RemotePolicyMonitor remotePolicies;
    final SharedFeatures shared;
    final LoggingService logging;
    final DiagnosticsService diagnostics;
    final @Nullable CommandService commands;
    final ScenarioDefinitions scenarios;
    final ModuleProductResolver productResolver;
    final ModuleMetadataFactory metadataFactory;
    final ModuleResourceFactory resourceFactory;
    final ModuleDependencyValidator dependencyValidator;
    final Map<Object, LoadedPlugin> plugins = new IdentityHashMap<>();
    final Map<String, ProductDescriptor> productDescriptors = new LinkedHashMap<>();
    final AtomicBoolean closed = new AtomicBoolean(false);
    final AtomicLong registrationSequence = new AtomicLong();

    ModuleHost(PlatformIdentity identity, ModulePreferences preferences, LibraryFetcher libraryFetcher,
               RemotePolicyMonitor remotePolicies, SharedFeatures shared, LoggingService logging, DiagnosticsService diagnostics,
               @Nullable CommandService commands, ScenarioDefinitions scenarios, ModuleProductResolver productResolver,
               ModuleMetadataFactory metadataFactory, ModuleResourceFactory resourceFactory,
               ModuleDependencyValidator dependencyValidator) {
        this.identity = identity;
        this.preferences = preferences;
        this.libraryFetcher = libraryFetcher;
        this.remotePolicies = remotePolicies;
        this.shared = shared;
        this.logging = logging;
        this.diagnostics = diagnostics;
        this.commands = commands;
        this.scenarios = scenarios;
        this.productResolver = productResolver;
        this.metadataFactory = metadataFactory;
        this.resourceFactory = resourceFactory;
        this.dependencyValidator = dependencyValidator;
    }

    LoadedPlugin detachPlugin(Object owner) {
        synchronized (plugins) {
            return plugins.remove(owner);
        }
    }
}
