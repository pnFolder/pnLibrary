package org.pnfolder.pnlibrary.core.menu.data;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.config.ConfigEditResult;
import org.pnfolder.pnlibrary.api.config.ConfigId;
import org.pnfolder.pnlibrary.api.config.ConfigSetting;
import org.pnfolder.pnlibrary.api.events.inspect.EventInfo;
import org.pnfolder.pnlibrary.api.events.inspect.ListenerInfo;
import org.pnfolder.pnlibrary.api.events.inspect.history.EventDay;
import org.pnfolder.pnlibrary.api.events.inspect.trace.EventTrace;
import org.pnfolder.pnlibrary.api.placeholders.info.PlaceholderInfo;
import org.pnfolder.pnlibrary.api.plugin.PluginRegistration;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.api.tasks.info.TaskSnapshot;
import org.pnfolder.pnlibrary.api.updates.state.UpdateActor;
import org.pnfolder.pnlibrary.core.commands.modules.DiskReport;
import org.pnfolder.pnlibrary.core.commands.modules.DiskUsage;
import org.pnfolder.pnlibrary.core.commands.modules.LibraryRow;
import org.pnfolder.pnlibrary.core.journal.JournalDetail;
import org.pnfolder.pnlibrary.core.journal.JournalGroup;
import org.pnfolder.pnlibrary.core.lifecycle.RuntimeBridge;
import org.pnfolder.pnlibrary.core.link.ServerGroup;
import org.pnfolder.pnlibrary.core.link.ServerGroups;
import org.pnfolder.pnlibrary.core.menu.OwnerInfo;
import org.pnfolder.pnlibrary.core.menu.PluginDirectory;
import org.pnfolder.pnlibrary.core.menu.screens.ConfigRow;
import org.pnfolder.pnlibrary.core.scenario.ScenarioDefinitionInfo;
import org.pnfolder.pnlibrary.core.stats.StatsSnapshot;
import org.pnfolder.pnlibrary.core.tasks.TaskControl;

/**
 * The facts of the library on this server. It is the source of this server's own menu, and the one that answers when
 * another server asks over the link.
 */
public final class LocalMenuData extends LocalMenuUpdates {
    private static final long DISK_REMEMBER_MILLIS = 30_000L;

    private final PluginDirectory directory;
    private final BiFunction<ViewerInfo, String, List<Component>> run;
    private final ServerGroups groups;
    private final LocalDependencyData dependencies;
    private final LocalSettingData settings;
    private final LocalEventData events;
    private volatile long diskAt;
    private volatile DiskReport diskCache = DiskReport.EMPTY;

    /**
     * @param library this server's library
     * @param directory the plugins of this platform
     * @param run runs a chat command for a viewer and returns what it printed
     * @param groups the groups of servers, on a proxy; null on a game server
     */
    public LocalMenuData(@NotNull PnLibrary library, @NotNull PluginDirectory directory,
                         @NotNull BiFunction<ViewerInfo, String, List<Component>> run, @Nullable ServerGroups groups) {
        super(library);
        this.directory = directory;
        this.run = run;
        this.groups = groups;
        this.dependencies = new LocalDependencyData(library);
        this.settings = new LocalSettingData(library);
        this.events = new LocalEventData(library, directory);
    }

    public LocalMenuData(@NotNull PnLibrary library, @NotNull PluginDirectory directory,
                         @NotNull BiFunction<ViewerInfo, String, List<Component>> run) {
        this(library, directory, run, null);
    }

    public LocalMenuData(@NotNull PnLibrary library, @NotNull PluginDirectory directory) {
        this(library, directory, (viewer, line) -> Collections.<Component>emptyList(), null);
    }

    @Override
    public @Nullable Boolean downloadsAutomatic() {
        return dependencies.automatic();
    }

    @Override
    public void setDownloadsAutomatic(boolean value) {
        dependencies.setAutomatic(value);
    }

    @Override
    public @NotNull List<MissingPlugins> missingDependencies() {
        return dependencies.missing();
    }

    @Override
    public @NotNull CompletionStage<String> installDependencies(@NotNull String code) {
        return dependencies.install(code);
    }

    @Override
    public @NotNull String version() {
        return library.getVersion();
    }

    @Override
    public @NotNull List<PluginRow> plugins() {
        List<PluginRow> rows = new ArrayList<>();
        for (PluginRegistration registration : library.getPlugins().all()) {
            OwnerInfo owner = directory.owner(registration.getOwner());
            rows.add(new PluginRow(owner != null ? owner.getName() : registration.getOwner().getClass().getSimpleName(),
                owner == null ? null : owner.getVersion(), registration.all().size()));
        }
        return rows;
    }

    @Override
    public @NotNull List<JournalGroup> journalGroups(@Nullable String level, int hours, int limit) {
        return RuntimeBridge.journalGroups(library, level, hours, limit);
    }

    @Override
    public @Nullable JournalDetail journalDetail(@NotNull String id) {
        return RuntimeBridge.journalDetail(library, id);
    }

    @Override
    public @NotNull List<TaskSnapshot> tasks() {
        return RuntimeBridge.taskSnapshots(library);
    }

    @Override
    public @NotNull List<ModuleInfo> modules() {
        return ModuleInfo.all(library);
    }

    @Override
    public @NotNull List<LibraryRow> libraries() {
        return LibraryRow.all(library);
    }

    /** The scan is remembered for half a minute: a big folder is not walked again for every click. */
    @Override
    public @NotNull DiskReport disk() {
        long now = System.currentTimeMillis();
        if (now - diskAt > DISK_REMEMBER_MILLIS) {
            Path plugins = DiskUsage.getPluginsFolder();
            diskCache = DiskUsage.scan(plugins, DiskUsage.registeredFiles(library));
            diskAt = now;
        }
        return diskCache;
    }

    @Override
    public @NotNull ModuleInfo moduleAction(@NotNull String key, @NotNull String action) {
        return ModuleInfo.apply(library, key, action);
    }

    @Override
    public @NotNull TaskControl runTask(@NotNull String id) {
        return RuntimeBridge.runTaskNow(library, id);
    }

    @Override
    public @NotNull TaskControl cancelTask(@NotNull String id) {
        return RuntimeBridge.cancelTask(library, id);
    }

    @Override
    public @NotNull StatsSnapshot stats() {
        return RuntimeBridge.statsSnapshot(library);
    }

    @Override
    public @NotNull List<PlaceholderInfo> placeholders() {
        return RuntimeBridge.placeholderInfos(library);
    }

    @Override
    public @NotNull List<ScenarioDefinitionInfo> scenarioDefinitions() {
        return RuntimeBridge.scenarioDefinitions(library);
    }

    @Override
    public @NotNull List<ConfigRow> configs() {
        return LocalConfigData.rows(library);
    }

    @Override
    public @NotNull List<ConfigSetting> configSettings(@NotNull String id) {
        return LocalConfigData.settings(library, ConfigId.parse(id));
    }

    @Override
    public @NotNull ConfigEditResult editConfig(@NotNull String id, @NotNull String path, @NotNull String text) {
        return LocalConfigData.edit(library, id, path, text);
    }

    @Override
    public @NotNull List<SettingRow> settings() {
        return settings.rows();
    }

    @Override
    public @NotNull SettingOutcome changeSetting(@NotNull String key, @Nullable String value, @NotNull UpdateActor actor) {
        return settings.change(key, value);
    }

    @Override
    public @NotNull List<EventInfo> events() {
        return events.events();
    }

    @Override
    public @NotNull List<ListenerInfo> listeners() {
        return events.listeners();
    }

    @Override
    public @NotNull List<EventDay> eventHistory(@Nullable String eventType) {
        return events.history(eventType);
    }

    @Override
    public int eventHistoryDays() {
        return events.historyDays();
    }

    @Override
    public @NotNull List<String> tracedEvents() {
        return events.tracedEvents();
    }

    @Override
    public @NotNull List<EventTrace> traces() {
        return events.traces();
    }

    @Override
    public void clearTraces() {
        events.clearTraces();
    }

    @Override
    public void setTracing(@NotNull String eventType, boolean enabled) {
        events.setTracing(eventType, enabled);
    }

    @Override
    public @Nullable String exportEventHistoryJson() {
        return events.exportJson();
    }

    @Override
    public @Nullable String exportEventHistoryCsv() {
        return events.exportCsv();
    }

    @Override
    public @Nullable EventAbout eventAbout(@NotNull String eventType) {
        return events.about(eventType);
    }

    @Override
    public @Nullable OwnerInfo ownerNamed(@NotNull String name) {
        return directory.ownerNamed(name);
    }

    @Override
    public @NotNull List<ServerGroup> groups() {
        return groups == null ? Collections.<ServerGroup>emptyList() : groups.all();
    }

    @Override
    public boolean editGroup(@NotNull GroupEdit edit) {
        return groups != null && GroupEdits.apply(groups, edit);
    }

    @Override
    public @NotNull List<Component> command(@NotNull ViewerInfo viewer, @NotNull String line) {
        return run.apply(viewer, line);
    }
}
