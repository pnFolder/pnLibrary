package org.pnfolder.pnlibrary.core.menu.data;

import org.pnfolder.pnlibrary.core.menu.data.question.EventAbout;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.config.ConfigEditResult;
import org.pnfolder.pnlibrary.api.config.ConfigSetting;
import org.pnfolder.pnlibrary.api.events.inspect.EventInfo;
import org.pnfolder.pnlibrary.api.events.inspect.ListenerInfo;
import org.pnfolder.pnlibrary.api.events.inspect.history.EventDay;
import org.pnfolder.pnlibrary.api.events.inspect.trace.EventTrace;
import org.pnfolder.pnlibrary.api.placeholders.info.PlaceholderInfo;
import org.pnfolder.pnlibrary.api.tasks.info.TaskSnapshot;
import org.pnfolder.pnlibrary.api.updates.backup.UpdateBackup;
import org.pnfolder.pnlibrary.api.updates.history.UpdateHistoryEntry;
import org.pnfolder.pnlibrary.api.updates.product.UpdateChannel;
import org.pnfolder.pnlibrary.api.updates.state.UpdateActor;
import org.pnfolder.pnlibrary.api.updates.state.UpdateSnapshot;
import org.pnfolder.pnlibrary.core.commands.modules.DiskReport;
import org.pnfolder.pnlibrary.core.commands.modules.LibraryRow;
import org.pnfolder.pnlibrary.core.journal.JournalDetail;
import org.pnfolder.pnlibrary.core.journal.JournalGroup;
import org.pnfolder.pnlibrary.core.link.ServerGroup;
import org.pnfolder.pnlibrary.core.menu.OwnerInfo;
import org.pnfolder.pnlibrary.core.menu.screens.config.ConfigRow;
import org.pnfolder.pnlibrary.core.scenario.ScenarioDefinitionInfo;
import org.pnfolder.pnlibrary.core.stats.StatsSnapshot;
import org.pnfolder.pnlibrary.core.tasks.TaskControl;

/**
 * Everything the menu reads from a server and everything it asks a server to do.
 *
 * <p>The menu is always built by the server the player stands on. It asks a {@code MenuData} for the facts: this
 * server's own library for its own menu, or another server's library, reached over the link, for the menu of that
 * server. A proxy therefore draws nothing; it answers these calls.
 *
 * <p>Methods that return a {@link CompletionStage} finish later; the others answer at once on this server and may
 * take a moment on another one, so the menu calls them away from the server thread.
 */
public interface MenuData {
    @NotNull String version();

    @NotNull List<UpdateSnapshot> snapshots();

    /** The update state of one plugin, or null when it did not register. */
    @Nullable UpdateSnapshot snapshot(@NotNull String product);

    @NotNull List<PluginRow> plugins();

    /** The current update plan, or null when there is none. */
    @Nullable PlanInfo plan();

    /** Until when updates of {@code product} are paused, or null. */
    @Nullable Instant heldUntil(@NotNull String product);

    @NotNull CompletionStage<Integer> historyCount(@Nullable String product);

    @NotNull CompletionStage<List<UpdateHistoryEntry>> historyPage(int page, @Nullable String product);

    @NotNull CompletionStage<List<UpdateBackup>> backups();

    @NotNull CompletionStage<Boolean> checkAll(@NotNull UpdateActor actor);

    /** Asks the catalog of one plugin for a new version; it does not wait for the answer. */
    void checkOne(@NotNull String product, @NotNull UpdateActor actor);

    /** Sets the update channel of {@code product}; null returns it to the default. */
    @NotNull CompletionStage<Boolean> setChannel(@NotNull String product, @Nullable UpdateChannel channel, @NotNull UpdateActor actor);

    /** Chooses the Java build of {@code product}; null returns it to the recommended one. */
    @NotNull CompletionStage<Boolean> setJavaBuild(@NotNull String product, @Nullable Integer java, @NotNull UpdateActor actor);

    /** Pauses updates of {@code product} for {@code duration}; null lifts the pause. */
    @NotNull CompletionStage<Boolean> setHeld(@NotNull String product, @Nullable Duration duration, @NotNull UpdateActor actor);

    @NotNull CompletionStage<Boolean> rollback(@NotNull UUID id, boolean restoreConfigurations, @NotNull UpdateActor actor);

    /** Puts the saved database {@code name} of {@code product} back from the set {@code id}; rows written since are lost. */
    @NotNull CompletionStage<Boolean> restoreDatabase(@NotNull UUID id, @NotNull String product, @NotNull String name, @NotNull UpdateActor actor);

    /** Copies the saved files of {@code product} from the set {@code id} into a folder and returns its path. */
    @NotNull CompletionStage<String> extractBackup(@NotNull UUID id, @NotNull String product, @NotNull UpdateActor actor);

    /** The journal groups of the last {@code hours} hours; the level is {@code info}, {@code warning}, {@code error} or null. */
    @NotNull List<JournalGroup> journalGroups(@Nullable String level, int hours, int limit);

    /** The whole journal event behind a group, or null when it is no longer there. */
    @Nullable JournalDetail journalDetail(@NotNull String id);

    @NotNull List<TaskSnapshot> tasks();

    @NotNull StatsSnapshot stats();

    @NotNull List<PlaceholderInfo> placeholders();

    /** Every named action, requirement and function with its owner and visibility. */
    @NotNull List<ScenarioDefinitionInfo> scenarioDefinitions();

    /** The configuration files of this server; another server's files are managed there, so the default is none. */
    default @NotNull List<ConfigRow> configs() {
        return Collections.emptyList();
    }

    /** The settings of one file that may be changed in game, with their values. */
    default @NotNull List<ConfigSetting> configSettings(@NotNull String id) {
        return Collections.emptyList();
    }

    /** Changes one setting of a file of this server and saves it. */
    default @NotNull ConfigEditResult editConfig(@NotNull String id, @NotNull String path, @NotNull String text) {
        throw new UnsupportedOperationException("Настройки меняются на самом сервере");
    }

    /** Every setting of the library of this server with its value; empty when it has none. */
    default @NotNull List<SettingRow> settings() {
        return Collections.emptyList();
    }

    /** Sets the setting {@code key} to {@code value}, or returns it to the default when {@code value} is null. */
    default @NotNull SettingOutcome changeSetting(@NotNull String key, @Nullable String value, @NotNull UpdateActor actor) {
        return new SettingOutcome(false, "Настройки меняются на самом сервере");
    }

    /** Whether missing plugins are downloaded without a command; null when this server has no such download. */
    default @Nullable Boolean downloadsAutomatic() {
        return null;
    }

    default void setDownloadsAutomatic(boolean value) {
    }

    /** Plugins other plugins need and the server lacks, grouped by who asks; none when nothing is missing or this server cannot say. */
    default @NotNull List<MissingPlugins> missingDependencies() {
        return Collections.emptyList();
    }

    /** Downloads the group with this {@code code} now and says in a sentence how it went. */
    default @NotNull CompletionStage<String> installDependencies(@NotNull String code) {
        return CompletableFuture.completedFuture("Здесь нечего скачивать.");
    }

    /** The modules of the plugins on this server; none when this server cannot switch them. */
    default @NotNull List<ModuleInfo> modules() {
        return Collections.emptyList();
    }

    /** The libraries plugins and modules of this server registered; none when this server cannot say. */
    default @NotNull List<LibraryRow> libraries() {
        return Collections.emptyList();
    }

    /** What each plugin takes on the disk of this server. */
    default @NotNull DiskReport disk() {
        return DiskReport.EMPTY;
    }

    /** Runs one of {@link ModuleInfo#ACTIONS} on the module {@code key} and returns how the module looks afterwards. */
    default @NotNull ModuleInfo moduleAction(@NotNull String key, @NotNull String action) {
        throw new UnsupportedOperationException("Модули переключаются на самом сервере");
    }

    @NotNull TaskControl runTask(@NotNull String id);

    @NotNull TaskControl cancelTask(@NotNull String id);

    @NotNull List<EventInfo> events();

    @NotNull List<ListenerInfo> listeners();

    @NotNull List<EventDay> eventHistory(@Nullable String eventType);

    /** How many days of calls are kept. */
    int eventHistoryDays();

    @NotNull List<String> tracedEvents();

    @NotNull List<EventTrace> traces();

    void clearTraces();

    void setTracing(@NotNull String eventType, boolean enabled);

    /** Writes the call history as JSON to a file and returns where, or null. */
    @Nullable String exportEventHistoryJson();

    /** Writes the call history as CSV to a file and returns where, or null. */
    @Nullable String exportEventHistoryCsv();

    /** The purpose and the owner of an event, or null when it is unknown. */
    @Nullable EventAbout eventAbout(@NotNull String eventType);

    @Nullable OwnerInfo ownerNamed(@NotNull String name);

    @NotNull List<ServerGroup> groups();

    /** Applies one change to the groups; returns false when there is no such group or the name is unfit. */
    boolean editGroup(@NotNull GroupEdit edit);

    @NotNull List<Component> command(@NotNull ViewerInfo viewer, @NotNull String line);
}
