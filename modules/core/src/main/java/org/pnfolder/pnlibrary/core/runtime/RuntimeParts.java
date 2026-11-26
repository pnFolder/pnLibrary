package org.pnfolder.pnlibrary.core.runtime;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;
import org.pnfolder.pnlibrary.api.logging.LogLevel;
import org.pnfolder.pnlibrary.api.logging.LoggingService;
import org.pnfolder.pnlibrary.api.metrics.PluginMetrics;
import org.pnfolder.pnlibrary.api.network.NetworkProxy;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryConfig;
import org.pnfolder.pnlibrary.api.tasks.info.TaskSnapshot;
import org.pnfolder.pnlibrary.common.time.DisplayZone;
import org.pnfolder.pnlibrary.core.commands.engine.StandardCommandFeedback;
import org.pnfolder.pnlibrary.core.commands.modules.DiskUsage;
import org.pnfolder.pnlibrary.core.diagnostics.DiagnosticsRegistry;
import org.pnfolder.pnlibrary.core.diagnostics.config.ConfigFileDiagnostics;
import org.pnfolder.pnlibrary.core.events.EventDispatcher;
import org.pnfolder.pnlibrary.core.events.inspect.history.EventHistory;
import org.pnfolder.pnlibrary.core.logging.DiagnosticCapture;
import org.pnfolder.pnlibrary.core.logging.ModuleLogging;
import org.pnfolder.pnlibrary.core.metrics.session.MetricsSessions;
import org.pnfolder.pnlibrary.core.observability.SupportRuntime;
import org.pnfolder.pnlibrary.core.plugin.LibraryWords;
import org.pnfolder.pnlibrary.core.plugin.LoadedPlugins;
import org.pnfolder.pnlibrary.core.plugin.SharedFeatures;
import org.pnfolder.pnlibrary.core.plugin.module.library.LibraryFetcher;
import org.pnfolder.pnlibrary.core.plugin.module.ModulePreferences;
import org.pnfolder.pnlibrary.core.remote.monitor.RemotePolicyMonitor;
import org.pnfolder.pnlibrary.core.remote.report.RemotePolicyNoticeRenderer;
import org.pnfolder.pnlibrary.core.runtime.details.RuntimeDiagnosticsAssembler;
import org.pnfolder.pnlibrary.core.runtime.details.RuntimeFeatureSet;
import org.pnfolder.pnlibrary.core.runtime.details.RuntimeJournal;
import org.pnfolder.pnlibrary.core.runtime.platform.NativePlatform;
import org.pnfolder.pnlibrary.core.runtime.platform.PlatformRuntimeContext;
import org.pnfolder.pnlibrary.core.runtime.platform.PlatformServiceSet;
import org.pnfolder.pnlibrary.core.settings.LegacySettingsImport;
import org.pnfolder.pnlibrary.core.settings.LibraryInfoFiles;
import org.pnfolder.pnlibrary.core.settings.SettingPickerHook;
import org.pnfolder.pnlibrary.core.settings.SettingsStore;
import org.pnfolder.pnlibrary.core.stats.StatsRuntime;
import org.pnfolder.pnlibrary.core.tasks.ScheduledTasks;
import org.pnfolder.pnlibrary.updates.paths.LegacyPathsMigration;
import org.pnfolder.pnlibrary.updates.paths.LibraryPaths;

/**
 * The runtime parts, created in dependency order. Support and the journal come first: every later part reports its
 * failures through them. {@link PnLibraryImpl} exposes these parts and starts them.
 */
final class RuntimeParts {
    /** The journal lives under {@code /pn} on every platform; chat buttons and notices link to it. */
    static final String EVENTS_COMMAND = "/pn events";

    final Object owner;
    final PlatformRuntimeContext platform;
    final DiagnosticsRegistry diagnostics;
    final long startedAtMillis = System.currentTimeMillis();
    final String version;
    final Path dataFolder;
    final LibraryPaths paths;
    final SettingsStore settings;
    final PnLibraryConfig config;
    final RuntimeJournal journalRuntime;
    final SupportRuntime support;
    final MetricsSessions metricsService;
    final LoggingService logging;
    final RuntimeFeatureSet features;
    final PlatformServiceSet platformServices;
    final ScheduledTasks tasks;
    final EventDispatcher eventService;
    final NativePlatform platformProvider = new NativePlatform();
    final @Nullable NetworkProxy proxy;
    final LoadedPlugins plugins;
    final StatsRuntime stats;
    final SettingPickerHook settingPicker = new SettingPickerHook();
    final Map<String, CommandDefinition> mountable = new LinkedHashMap<>();
    private volatile PluginMetrics libraryMetrics;
    private RuntimeDiagnosticsAssembler runtimeDiagnostics;

    RuntimeParts(Object owner, PlatformRuntimeContext platform, DiagnosticsRegistry diagnostics, @Nullable PnLibraryConfig presetConfig,
                 BooleanSupplier closed) {
        this.owner = owner;
        this.platform = platform;
        this.diagnostics = diagnostics;
        Map<String, String> details = platform.getOwnership().ownerDetails(owner);
        this.version = details.get("version") != null ? details.get("version") : "unknown";
        this.dataFolder = resolveDataFolder();
        DiskUsage.setPluginsFolder(dataFolder.toAbsolutePath().getParent());
        this.paths = new LibraryPaths(dataFolder);
        Consumer<String> warn = text -> platform.getLogging().log(owner, LogLevel.WARNING, text);
        this.settings = openSettings(warn);
        this.config = presetConfig != null ? presetConfig : settings.config();
        followSettings(warn);
        this.journalRuntime = new RuntimeJournal(paths.getJournal(), config, platform.getOwnership(), startedAtMillis,
                EVENTS_COMMAND, this::reportJournalFailure);
        this.support = createSupport();
        this.metricsService = new MetricsSessions(platform.getMetricsFactory(), platform.getIdentity().getType());
        this.logging = new ModuleLogging(platform.getLogging(), platform.getOwnership(), platform.getDiagnostics(),
                platform.getIdentity(), support.getCapture());
        this.features = createFeatures();
        this.platformServices = createPlatformServices();
        this.tasks = platformServices.getTasks();
        this.eventService = createEvents();
        this.proxy = platform.getNetworkProxy();
        this.plugins = createPlugins();
        this.runtimeDiagnostics = createRuntimeDiagnostics(closed);
        this.stats = new StatsRuntime(this::taskSnapshots, () -> eventService.getInspector().events(), journalRuntime.getStore(),
                error -> support.recordRuntimeError(owner, "[pnLibrary/stats] Could not record statistics", error));
    }

    private Path resolveDataFolder() {
        if (platform.getDataFolder() != null) {
            return platform.getDataFolder();
        }
        Path fromOwner = platform.getOwnership().ownerDataFolder(owner);
        return fromOwner != null ? fromOwner : Paths.get("plugins", owner.getClass().getSimpleName());
    }

    private SettingsStore openSettings(Consumer<String> warn) {
        // The old files are moved into data and read once, before the settings are used.
        new LegacyPathsMigration(paths, warn).run();
        SettingsStore store = new SettingsStore(paths.getSettings(), warn);
        new LegacySettingsImport(paths, warn).run(store);
        return store;
    }

    private void followSettings(Consumer<String> warn) {
        DisplayZone.setZone(ZoneId.of(settings.current().getTimeZone()));
        settings.onChange(current -> DisplayZone.setZone(ZoneId.of(current.getTimeZone())));
        // Help and settings overview for administrators: written again at every start and after every change.
        final LibraryInfoFiles files = new LibraryInfoFiles(paths, settings::values, version, warn);
        files.refresh();
        settings.onChange(current -> files.refreshSettings());
    }

    private SupportRuntime createSupport() {
        return new SupportRuntime(owner, platform.getIdentity(), platform.getDiagnostics(), platform.getLogObservation(),
                (logs, onError) -> new DiagnosticCapture(platform.getOwnership(), platform.getLogging(), logs,
                        journalRuntime::record, (logOwner, message, error) -> onError.accept(logOwner, message, error)),
                diagnostics, config, paths, () -> runtimeDiagnostics.assemble(), this::reportRuntimeError);
    }

    private RuntimeFeatureSet createFeatures() {
        RuntimeFeatureSet set = new RuntimeFeatureSet(owner, platform, paths, support.getCapture(), settings);
        set.getConfigurations().setFileListener(file -> {
            final List<AutoCloseable> handles = new ArrayList<>();
            addIfPresent(handles, ConfigFileDiagnostics.reportConfigFile(diagnostics, file));
            addIfPresent(handles, set.getUpdates().getDatabaseSources().register(file));
            return () -> {
                for (AutoCloseable handle : handles) {
                    handle.close();
                }
            };
        });
        LibraryWords.register(set.getConfigurations().scope(owner));
        return set;
    }

    private static void addIfPresent(List<AutoCloseable> handles, @Nullable AutoCloseable handle) {
        if (handle != null) {
            handles.add(handle);
        }
    }

    private PlatformServiceSet createPlatformServices() {
        return new PlatformServiceSet(platform.getCommandAdapter(), platform.getPlayerAdapter(), platform.getTaskAdapter(),
                support.getCapture(), config.getTaskHistoryCapacity(), paths.getTaskHistory(), support::recordRuntimeError,
                StandardCommandFeedback.from(config),
                error -> support.recordRuntimeError(owner, "[pnLibrary/players] A message could not be delivered to a player", error));
    }

    private EventDispatcher createEvents() {
        EventHistory history = new EventHistory(paths.getEventHistory(), config.getEventHistoryDays(), DisplayZone::getZone,
                error -> support.recordRuntimeError(owner, "[pnLibrary/events] The event history could not be read or saved", error));
        return new EventDispatcher(history, (pluginId, message, error) ->
                support.recordRuntimeError(owner, "[" + pluginId + "] " + message, error));
    }

    private LoadedPlugins createPlugins() {
        RemotePolicyMonitor remotePolicies = new RemotePolicyMonitor(platform.getOwnership(), platform.getExecution(),
                new RemotePolicyNoticeRenderer(support.getCapture()));
        SharedFeatures shared = new SharedFeatures(features.getPlaceholders(), features.getCurrency(), features.getExpansions(),
                features.getLibraryExpansions());
        return new LoadedPlugins(platform.getOwnership(), platform.getIdentity(), remotePolicies, eventService, tasks,
                features.getServices(), logging, metricsService, diagnostics, features.getUpdates(), shared,
                features.getConfigurations(), platformServices.getCommands(), features.getDownloads(), version,
                new ModulePreferences(dataFolder.resolve("modules").resolve("preferences.json")), LibraryFetcher.THROUGH_LIBBY);
    }

    private RuntimeDiagnosticsAssembler createRuntimeDiagnostics(BooleanSupplier closed) {
        return new RuntimeDiagnosticsAssembler(version, closed, platform.getIdentity(), dataFolder, config, tasks,
                features.getUpdates(), diagnostics, support, metricsService, platformServices.getCommands(), journalRuntime::summary);
    }

    @NotNull List<TaskSnapshot> taskSnapshots() {
        List<TaskSnapshot> visible = new ArrayList<>();
        for (TaskSnapshot task : tasks.query()) {
            if (!task.getHidden()) {
                visible.add(task);
            }
        }
        return visible;
    }

    @Nullable PluginMetrics getLibraryMetrics() {
        return libraryMetrics;
    }

    void setLibraryMetrics(@Nullable PluginMetrics metrics) {
        this.libraryMetrics = metrics;
    }

    /** The journal cannot record its own failure, so the console and the error tracker get it directly. */
    private void reportJournalFailure(Throwable error) {
        platform.getLogging().console(owner, "[pnLibrary/journal] Could not write to the journal: "
                + error.getClass().getSimpleName() + ": " + error.getMessage());
        reportRuntimeError(owner, error);
    }

    private void reportRuntimeError(Object logOwner, Throwable error) {
        PluginMetrics metrics = libraryMetrics;
        if (metrics != null && metrics.getErrorReporter() != null) {
            metrics.getErrorReporter().capture(error, "pnlibrary.runtime", true,
                    Collections.singletonMap("source", logOwner.getClass().getName()));
        }
    }
}
