package org.pnfolder.pnlibrary.core.lifecycle;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.api.commands.CommandRegistration;
import org.pnfolder.pnlibrary.api.commands.definition.CommandBuilder;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;
import org.pnfolder.pnlibrary.api.placeholders.info.PlaceholderInfo;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
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

/**
 * Reaches {@link RuntimeResources} through a {@link PnLibrary}. Consumer plugins get tasks through
 * {@code ModuleContext.getTasks()}; this bridge is for core and platforms only.
 */
public final class RuntimeBridge {
    private RuntimeBridge() {
    }

    @Nullable
    public static DownloadService dependencyDownloads(@NotNull PnLibrary library) {
        RuntimeResources resources = resources(library);
        return resources == null ? null : resources.dependencyDownloads();
    }

    @Nullable
    public static SettingPickerHook settingPicker(@NotNull PnLibrary library) {
        RuntimeResources resources = resources(library);
        return resources == null ? null : resources.getSettingPicker();
    }

    @Nullable
    public static SettingsStore librarySettings(@NotNull PnLibrary library) {
        RuntimeResources resources = resources(library);
        return resources == null ? null : resources.librarySettings();
    }

    @NotNull
    public static List<TaskSnapshot> taskSnapshots(@NotNull PnLibrary library) {
        RuntimeResources resources = resources(library);
        return resources == null ? Collections.emptyList() : resources.taskSnapshots();
    }

    @NotNull
    public static TaskControl cancelTask(@NotNull PnLibrary library, @NotNull String id) {
        RuntimeResources resources = resources(library);
        return resources == null ? TaskControl.UNAVAILABLE : resources.cancelTask(id);
    }

    @NotNull
    public static TaskControl runTaskNow(@NotNull PnLibrary library, @NotNull String id) {
        RuntimeResources resources = resources(library);
        return resources == null ? TaskControl.UNAVAILABLE : resources.runTaskNow(id);
    }

    @Nullable
    public static JournalDetail journalDetail(@NotNull PnLibrary library, @NotNull String id) {
        RuntimeResources resources = resources(library);
        return resources == null ? null : resources.journalDetail(id);
    }

    @NotNull
    public static List<JournalGroup> journalGroups(@NotNull PnLibrary library, @Nullable String level, int sinceHours, int limit) {
        RuntimeResources resources = resources(library);
        return resources == null ? Collections.emptyList() : resources.journalGroups(level, sinceHours, limit);
    }

    @NotNull
    public static List<JournalGroup> journalGroups(@NotNull PnLibrary library) {
        return journalGroups(library, null, 24, 200);
    }

    @NotNull
    public static TaskScope runtimeTaskScope(@NotNull PnLibrary library) {
        return runtimeResources(library).getRuntimeTasks();
    }

    @NotNull
    public static List<Component> journalJoinNotice(@NotNull PnLibrary library) {
        return runtimeResources(library).journalJoinNotice();
    }

    @NotNull
    public static StatsSnapshot statsSnapshot(@NotNull PnLibrary library) {
        return runtimeResources(library).statsSnapshot();
    }

    @NotNull
    public static List<PlaceholderInfo> placeholderInfos(@NotNull PnLibrary library) {
        RuntimeResources resources = resources(library);
        return resources == null ? Collections.emptyList() : resources.placeholderInfos();
    }

    @NotNull
    public static List<ScenarioDefinitionInfo> scenarioDefinitions(@NotNull PnLibrary library) {
        RuntimeResources resources = resources(library);
        return resources == null ? Collections.emptyList() : resources.scenarioDefinitions();
    }

    @NotNull
    public static CommandRegistration registerRuntimeCommand(@NotNull PnLibrary library, @NotNull CommandDefinition command) {
        return runtimeResources(library).registerRuntimeCommand(command);
    }

    @NotNull
    public static CommandRegistration registerRuntimeCommand(
            @NotNull PnLibrary library, @NotNull String name, @NotNull Consumer<CommandBuilder> configure) {
        return registerRuntimeCommand(library, CommandDefinition.command(name, configure));
    }

    @Nullable
    public static CommandDefinition mountableCommand(@NotNull PnLibrary library, @NotNull String name) {
        RuntimeResources resources = resources(library);
        return resources == null ? null : resources.mountableCommand(name);
    }

    @Nullable
    public static CompletionStage<Void> dispatchRuntimeCommand(
            @NotNull PnLibrary library, @NotNull CommandDefinition command, @NotNull CommandContext context) {
        RuntimeResources resources = resources(library);
        return resources == null ? null : resources.dispatchCommand(command, context);
    }

    @Nullable
    private static RuntimeResources resources(PnLibrary library) {
        return library instanceof RuntimeResources ? (RuntimeResources) library : null;
    }

    private static RuntimeResources runtimeResources(PnLibrary library) {
        RuntimeResources resources = resources(library);
        if (resources == null) {
            throw new IllegalStateException(
                    "Runtime resources are available only on the pnLibrary core runtime, not on " + library.getClass().getName());
        }
        return resources;
    }
}
