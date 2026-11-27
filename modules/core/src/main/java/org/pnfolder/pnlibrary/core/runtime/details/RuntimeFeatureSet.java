package org.pnfolder.pnlibrary.core.runtime.details;

import org.pnfolder.pnlibrary.updates.policy.UpdateDefaults;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.currency.CurrencyProviderRegistry;
import org.pnfolder.pnlibrary.api.logging.LogLevel;
import org.pnfolder.pnlibrary.config.YamlConfigurations;
import org.pnfolder.pnlibrary.core.downloads.DownloadConfiguration;
import org.pnfolder.pnlibrary.core.downloads.DownloadService;
import org.pnfolder.pnlibrary.core.expansions.ExpansionRegistry;
import org.pnfolder.pnlibrary.core.expansions.LocalizationExpansion;
import org.pnfolder.pnlibrary.core.placeholders.PlaceholderEngine;
import org.pnfolder.pnlibrary.core.runtime.platform.PlatformRuntimeContext;
import org.pnfolder.pnlibrary.core.services.ServiceRegistry;
import org.pnfolder.pnlibrary.core.settings.SettingsStore;
import org.pnfolder.pnlibrary.core.text.ConsoleStyle;
import org.pnfolder.pnlibrary.currency.CurrencyFeature;
import org.pnfolder.pnlibrary.currency.CurrencyRuntime;
import org.pnfolder.pnlibrary.spi.platform.logging.PlatformLogging;
import org.pnfolder.pnlibrary.updates.paths.LibraryPaths;
import org.pnfolder.pnlibrary.updates.policy.UpdateTiming;
import org.pnfolder.pnlibrary.updates.service.PluginUpdates;

/** Creates the feature services of the library runtime (services, configs, updates and so on). */
public final class RuntimeFeatureSet {
    private final ServiceRegistry services = new ServiceRegistry();
    private final YamlConfigurations configurations;
    private final PluginUpdates updates;
    private final DownloadService downloads;
    private final PlaceholderEngine placeholders;
    private final CurrencyRuntime currency;
    private final ExpansionRegistry expansions;
    private final ExpansionRegistry libraryExpansions;

    /** {@code owner} is the pnLibrary plugin that log lines are written for. */
    public RuntimeFeatureSet(@NotNull final Object owner, @NotNull PlatformRuntimeContext platform, @NotNull LibraryPaths paths,
                             @NotNull final PlatformLogging logging, @NotNull final SettingsStore settings) {
        this.configurations = new YamlConfigurations(platform.getOwnership(), logging);
        this.updates = createUpdates(platform, paths, logging, settings);
        this.downloads = new DownloadService(platform.getIdentity(), platform.getOwnership(), platform.getOwnership(),
                platform.getReadiness(), logging, paths, () -> DownloadConfiguration.of(settings.current()));
        this.placeholders = new PlaceholderEngine(problem -> logging.log(owner, LogLevel.WARNING, problem));
        this.currency = new CurrencyFeature((message, error) -> logging.log(owner, LogLevel.ERROR, message, error));
        // Expansions of plugins are public; the library's own ones are seen by no plugin and reached only through a module context.
        this.expansions = new ExpansionRegistry((expansion, error) -> logging.log(owner, LogLevel.WARNING,
                "Expansion '" + expansion.getId() + "' failed: " + error.getMessage(), error));
        this.libraryExpansions = new ExpansionRegistry((expansion, error) -> logging.log(owner, LogLevel.WARNING,
                "Expansion '" + expansion.getId() + "' failed: " + error.getMessage(), error));
        services.register(CurrencyProviderRegistry.class, currency.getProviders());
        // Item and entity names exist on game servers only; proxies get no loader and no cache folder.
        if (platform.getIdentity().isServer()) {
            libraryExpansions.register(new LocalizationExpansion(paths.getLocalization()));
        }
    }

    private static PluginUpdates createUpdates(PlatformRuntimeContext platform, LibraryPaths paths, PlatformLogging logging,
                                               SettingsStore settings) {
        try {
            return new PluginUpdates(logging, platform.getOwnership(), platform.getOwnership(), platform.getIdentity(),
                    platform.getReadiness(), paths, ConsoleStyle.THEME,
                    new UpdateTiming(settings.current().getUpdateCheckInterval(), UpdateDefaults.CONNECT_TIMEOUT,
                            UpdateDefaults.READ_TIMEOUT));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    public @NotNull ServiceRegistry getServices() {
        return services;
    }

    public @NotNull YamlConfigurations getConfigurations() {
        return configurations;
    }

    public @NotNull PluginUpdates getUpdates() {
        return updates;
    }

    public @NotNull DownloadService getDownloads() {
        return downloads;
    }

    public @NotNull PlaceholderEngine getPlaceholders() {
        return placeholders;
    }

    public @NotNull CurrencyRuntime getCurrency() {
        return currency;
    }

    /** Expansions of plugins; the public {@code PnLibrary.getExpansions()}. */
    public @NotNull ExpansionRegistry getExpansions() {
        return expansions;
    }

    /** The library's own expansions, which no plugin can see or control; modules reach them only through their context. */
    public @NotNull ExpansionRegistry getLibraryExpansions() {
        return libraryExpansions;
    }
}
