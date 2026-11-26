package org.pnfolder.pnlibrary.core.lifecycle;

import java.util.List;
import java.util.concurrent.CompletionStage;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.api.commands.CommandRegistration;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;
import org.pnfolder.pnlibrary.api.placeholders.info.PlaceholderInfo;
import org.pnfolder.pnlibrary.api.tasks.TaskScope;
import org.pnfolder.pnlibrary.api.tasks.info.TaskSnapshot;
import org.pnfolder.pnlibrary.core.downloads.DownloadService;
import org.pnfolder.pnlibrary.core.journal.JournalDetail;
import org.pnfolder.pnlibrary.core.journal.JournalGroup;
import org.pnfolder.pnlibrary.core.scenario.ScenarioDefinitionInfo;
import org.pnfolder.pnlibrary.core.settings.SettingPickerHook;
import org.pnfolder.pnlibrary.core.settings.SettingsStore;
import org.pnfolder.pnlibrary.core.stats.StatsSnapshot;
import org.pnfolder.pnlibrary.core.tasks.TaskControl;

/** What the core runtime offers to the library's own commands and menus; reached through {@link RuntimeBridge}. */
public interface RuntimeResources {
    @NotNull
    TaskScope getRuntimeTasks();

    @NotNull
    CommandRegistration registerRuntimeCommand(@NotNull CommandDefinition command);

    @Nullable
    CommandDefinition mountableCommand(@NotNull String name);

    @Nullable
    CompletionStage<Void> dispatchCommand(@NotNull CommandDefinition command, @NotNull CommandContext context);

    @NotNull
    List<Component> journalJoinNotice();

    @NotNull
    List<JournalGroup> journalGroups(@Nullable String level, int sinceHours, int limit);

    @NotNull
    List<TaskSnapshot> taskSnapshots();

    @NotNull
    StatsSnapshot statsSnapshot();

    @NotNull
    List<PlaceholderInfo> placeholderInfos();

    @NotNull
    List<ScenarioDefinitionInfo> scenarioDefinitions();

    @NotNull
    TaskControl cancelTask(@NotNull String id);

    @NotNull
    TaskControl runTaskNow(@NotNull String id);

    @Nullable
    DownloadService dependencyDownloads();

    @Nullable
    SettingsStore librarySettings();

    @NotNull
    SettingPickerHook getSettingPicker();

    @Nullable
    JournalDetail journalDetail(@NotNull String id);
}
