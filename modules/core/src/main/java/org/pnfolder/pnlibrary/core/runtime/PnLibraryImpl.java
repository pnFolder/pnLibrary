package org.pnfolder.pnlibrary.core.runtime;

import java.util.Objects;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.audience.Audiences;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.api.commands.CommandRegistration;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;
import org.pnfolder.pnlibrary.api.config.ConfigurationService;
import org.pnfolder.pnlibrary.api.diagnostics.DebugRequest;
import org.pnfolder.pnlibrary.api.diagnostics.report.DiagnosticReport;
import org.pnfolder.pnlibrary.api.events.bus.EventService;
import org.pnfolder.pnlibrary.api.expansions.ExpansionManager;
import org.pnfolder.pnlibrary.api.logging.LoggingService;
import org.pnfolder.pnlibrary.api.metrics.MetricsService;
import org.pnfolder.pnlibrary.api.network.NetworkProxy;
import org.pnfolder.pnlibrary.api.network.channel.ServerNetwork;
import org.pnfolder.pnlibrary.api.observability.ObservabilityService;
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderAdapterRegistry;
import org.pnfolder.pnlibrary.api.placeholders.info.PlaceholderInfo;
import org.pnfolder.pnlibrary.api.platform.PlatformProvider;
import org.pnfolder.pnlibrary.api.plugin.PluginRegistry;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryConfig;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryProvider;
import org.pnfolder.pnlibrary.api.services.ServiceManager;
import org.pnfolder.pnlibrary.api.tasks.TaskScope;
import org.pnfolder.pnlibrary.api.tasks.info.TaskId;
import org.pnfolder.pnlibrary.api.tasks.info.TaskSnapshot;
import org.pnfolder.pnlibrary.api.updates.UpdateService;
import org.pnfolder.pnlibrary.api.version.SemanticVersion;
import org.pnfolder.pnlibrary.common.lang.Failures;
import org.pnfolder.pnlibrary.core.diagnostics.DiagnosticsRegistry;
import org.pnfolder.pnlibrary.core.downloads.DownloadService;
import org.pnfolder.pnlibrary.core.exceptions.ResourceCleanup;
import org.pnfolder.pnlibrary.core.journal.JournalDetail;
import org.pnfolder.pnlibrary.core.journal.JournalGroup;
import org.pnfolder.pnlibrary.core.lifecycle.RuntimeResources;
import org.pnfolder.pnlibrary.core.runtime.lifecycle.RuntimeShutdownPlan;
import org.pnfolder.pnlibrary.core.runtime.platform.PlatformRegistration;
import org.pnfolder.pnlibrary.core.runtime.platform.PlatformRuntimeContext;
import org.pnfolder.pnlibrary.core.scenario.ScenarioDefinitionInfo;
import org.pnfolder.pnlibrary.core.settings.SettingPickerHook;
import org.pnfolder.pnlibrary.core.settings.SettingsStore;
import org.pnfolder.pnlibrary.core.stats.StatsSnapshot;
import org.pnfolder.pnlibrary.core.tasks.TaskControl;
import org.pnfolder.pnlibrary.spi.commands.PlatformCommandDispatcher;

/**
 * Internal facade over the runtime parts; {@link #init} starts them. Platform modules depend on {@link PnLibrary} and
 * must not cast the facade to this class.
 */
public final class PnLibraryImpl implements PnLibrary, PlatformRegistration, RuntimeResources {
    private final AtomicBoolean closedFlag = new AtomicBoolean(false);
    private final Runnable onClose;
    private final RuntimeParts parts;

    public PnLibraryImpl(@NotNull Object owner, @NotNull PlatformRuntimeContext platform, @NotNull DiagnosticsRegistry diagnostics,
                         @Nullable PnLibraryConfig presetConfig, @NotNull Runnable onClose) {
        this.onClose = Objects.requireNonNull(onClose, "onClose");
        this.parts = new RuntimeParts(owner, platform, diagnostics, presetConfig, closedFlag::get);
    }

    public PnLibraryImpl(@NotNull Object owner, @NotNull PlatformRuntimeContext platform, @NotNull DiagnosticsRegistry diagnostics) {
        this(owner, platform, diagnostics, null, () -> { });
    }

    public @NotNull java.nio.file.Path getDataFolder() {
        return parts.dataFolder;
    }

    /** Starts the parts and mounts the commands the platform's {@code /pn} control command offers. */
    public void init() {
        new RuntimeStart(parts, this).run();
    }

    @Override
    public @NotNull Object getOwner() {
        return parts.owner;
    }

    @Override
    public @NotNull DiagnosticsRegistry getDiagnostics() {
        return parts.diagnostics;
    }

    @Override
    public @NotNull PnLibraryConfig getConfiguration() {
        return parts.config;
    }

    @Override
    public boolean isClosed() {
        return closedFlag.get();
    }

    @Override
    public @NotNull String getVersion() {
        return parts.version;
    }

    @Override
    public @NotNull LoggingService getLogging() {
        return parts.logging;
    }

    @Override
    public @NotNull ObservabilityService getObservability() {
        return parts.support.getService();
    }

    @Override
    public @NotNull MetricsService getMetrics() {
        return parts.metricsService;
    }

    @Override
    public @NotNull ConfigurationService getConfigurations() {
        return parts.features.getConfigurations();
    }

    @Override
    public @NotNull UpdateService getUpdates() {
        return parts.features.getUpdates();
    }

    @Override
    public @NotNull Audiences getAudiences() {
        return parts.platformServices.getAudiences();
    }

    @Override
    public @NotNull ServiceManager getServices() {
        return parts.features.getServices();
    }

    @Override
    public @NotNull ExpansionManager getExpansions() {
        return parts.features.getExpansions();
    }

    @Override
    public @NotNull EventService getEvents() {
        return parts.eventService;
    }

    @Override
    public @NotNull PlatformProvider getPlatforms() {
        return parts.platformProvider;
    }

    @Override
    public @Nullable NetworkProxy getProxy() {
        return parts.proxy;
    }

    @Override
    public @Nullable ServerNetwork getServerNetwork() {
        return parts.serverNetwork;
    }

    @Override
    public @NotNull PlaceholderAdapterRegistry getPlaceholderAdapters() {
        return parts.features.getPlaceholders();
    }

    @Override
    public @NotNull PluginRegistry getPlugins() {
        return parts.plugins;
    }

    @Override
    public void close() {
        if (!closedFlag.compareAndSet(false, true)) {
            return;
        }
        new RuntimeShutdownPlan(parts.plugins, parts.platformServices.getCommands(), parts.platformServices.getAudiences(),
                parts.platformProvider, parts.features.getCurrency(), parts.features.getExpansions(),
                parts.features.getLibraryExpansions(), parts.features.getConfigurations(), parts::getLibraryMetrics,
                parts.metricsService, parts.features.getUpdates(), parts.eventService::close, parts.features.getServices(),
                parts.tasks,
                () -> ResourceCleanup.closeAll(parts.stats::close, parts.support::close, parts.journalRuntime::close),
                () -> PnLibraryProvider.clear(this), parts.platform.getShutdown()::close, onClose::run).run();
        parts.setLibraryMetrics(null);
    }

    @Override
    public boolean isAtLeastVersion(@NotNull String minimumVersion) {
        SemanticVersion current = SemanticVersion.tryParse(parts.version);
        return current != null && current.isAtLeast(minimumVersion);
    }

    @Override
    public @NotNull DiagnosticReport createDiagnosticReport(@NotNull DebugRequest request) {
        if (isClosed()) {
            throw new IllegalStateException("pnLibrary instance is closed");
        }
        try {
            return parts.support.createDiagnosticReport(request);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    @Override
    public @NotNull TaskScope getRuntimeTasks() {
        return parts.tasks.scope(parts.owner);
    }

    @Override
    public @NotNull CommandRegistration registerRuntimeCommand(@NotNull CommandDefinition command) {
        return parts.platformServices.getCommands().register(parts.owner, command);
    }

    @Override
    public @Nullable CommandDefinition mountableCommand(@NotNull String name) {
        return parts.mountable.get(name);
    }

    @Override
    public @Nullable CompletionStage<Void> dispatchCommand(@NotNull CommandDefinition command, @NotNull CommandContext context) {
        Object commands = parts.platformServices.getCommands();
        return commands instanceof PlatformCommandDispatcher ? ((PlatformCommandDispatcher) commands).execute(command, context) : null;
    }

    @Override
    public <T> @NotNull AutoCloseable registerPlatform(@NotNull Class<T> type, @NotNull T implementation) {
        return parts.platformProvider.register(type, implementation);
    }

    @Override
    public @Nullable DownloadService dependencyDownloads() {
        return parts.features.getDownloads();
    }

    @Override
    public @NotNull SettingsStore librarySettings() {
        return parts.settings;
    }

    @Override
    public @NotNull SettingPickerHook getSettingPicker() {
        return parts.settingPicker;
    }

    @Override
    public @NotNull List<TaskSnapshot> taskSnapshots() {
        return parts.taskSnapshots();
    }

    @Override
    public @NotNull TaskControl cancelTask(@NotNull String id) {
        return Failures.runCatchingNonFatal(() -> parts.tasks.cancelByAdmin(new TaskId(id))).getOrDefault(TaskControl.UNAVAILABLE);
    }

    @Override
    public @NotNull TaskControl runTaskNow(@NotNull String id) {
        return Failures.runCatchingNonFatal(() -> parts.tasks.runNow(new TaskId(id))).getOrDefault(TaskControl.UNAVAILABLE);
    }

    @Override
    public @NotNull List<Component> journalJoinNotice() {
        return parts.journalRuntime.getQueries().joinNotice();
    }

    @Override
    public @NotNull List<JournalGroup> journalGroups(@Nullable String level, int sinceHours, int limit) {
        return parts.journalRuntime.getQueries().groups(level, sinceHours, limit);
    }

    @Override
    public @Nullable JournalDetail journalDetail(@NotNull String id) {
        return parts.journalRuntime.getQueries().detail(id);
    }

    @Override
    public @NotNull StatsSnapshot statsSnapshot() {
        return parts.stats.snapshot();
    }

    @Override
    public @NotNull List<PlaceholderInfo> placeholderInfos() {
        return parts.features.getPlaceholders().list();
    }

    @Override
    public @NotNull List<ScenarioDefinitionInfo> scenarioDefinitions() {
        return parts.plugins.scenarioDefinitionInfos();
    }
}
