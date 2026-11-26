package org.pnfolder.pnlibrary.core.menu.data;

import java.util.List;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.placeholders.info.PlaceholderInfo;
import org.pnfolder.pnlibrary.api.tasks.info.TaskSnapshot;
import org.pnfolder.pnlibrary.api.updates.state.UpdateActor;
import org.pnfolder.pnlibrary.core.commands.modules.DiskReport;
import org.pnfolder.pnlibrary.core.commands.modules.LibraryRow;
import org.pnfolder.pnlibrary.core.journal.JournalDetail;
import org.pnfolder.pnlibrary.core.journal.JournalGroup;
import org.pnfolder.pnlibrary.core.link.ServerGroup;
import org.pnfolder.pnlibrary.core.link.remote.DataClient;
import org.pnfolder.pnlibrary.core.scenario.ScenarioDefinitionInfo;
import org.pnfolder.pnlibrary.core.stats.StatsSnapshot;
import org.pnfolder.pnlibrary.core.tasks.TaskControl;

/** The journal, task, module, settings and group questions of {@link RemoteMenuData}. */
abstract class RemoteSystemData extends RemoteEventData {
    RemoteSystemData(String target, DataClient client) {
        super(target, client);
    }

    @Override
    public @NotNull List<JournalGroup> journalGroups(@Nullable String level, int hours, int limit) {
        return ask(MenuCalls.JOURNAL_GROUPS, MenuWire.listOf(JournalGroup.class),
            args("level", level, "hours", hours, "limit", limit));
    }

    @Override
    public @Nullable JournalDetail journalDetail(@NotNull String id) {
        return ask(MenuCalls.JOURNAL_DETAIL, JournalDetail.class, args("id", id));
    }

    @Override
    public @NotNull List<TaskSnapshot> tasks() {
        return ask(MenuCalls.TASKS, MenuWire.listOf(TaskSnapshot.class));
    }

    @Override
    public @NotNull List<ModuleInfo> modules() {
        return ask(MenuCalls.MODULES, MenuWire.listOf(ModuleInfo.class));
    }

    @Override
    public @NotNull List<LibraryRow> libraries() {
        return ask(MenuCalls.LIBRARIES, MenuWire.listOf(LibraryRow.class));
    }

    @Override
    public @NotNull DiskReport disk() {
        return ask(MenuCalls.DISK, DiskReport.class);
    }

    @Override
    public @NotNull ModuleInfo moduleAction(@NotNull String key, @NotNull String action) {
        return ask(MenuCalls.MODULE_ACTION, ModuleInfo.class, args("key", key, "action", action));
    }

    @Override
    public @NotNull StatsSnapshot stats() {
        return ask(MenuCalls.STATS, StatsSnapshot.class);
    }

    @Override
    public @NotNull List<PlaceholderInfo> placeholders() {
        return ask(MenuCalls.PLACEHOLDERS, MenuWire.listOf(PlaceholderInfo.class));
    }

    @Override
    public @NotNull List<ScenarioDefinitionInfo> scenarioDefinitions() {
        return ask(MenuCalls.SCENARIO_DEFINITIONS, MenuWire.listOf(ScenarioDefinitionInfo.class));
    }

    @Override
    public @NotNull TaskControl runTask(@NotNull String id) {
        return ask(MenuCalls.RUN_TASK, TaskControl.class, args("id", id));
    }

    @Override
    public @NotNull TaskControl cancelTask(@NotNull String id) {
        return ask(MenuCalls.CANCEL_TASK, TaskControl.class, args("id", id));
    }

    @Override
    public @NotNull List<SettingRow> settings() {
        return ask(MenuCalls.SETTINGS, MenuWire.listOf(SettingRow.class));
    }

    @Override
    public @NotNull SettingOutcome changeSetting(@NotNull String key, @Nullable String value, @NotNull UpdateActor actor) {
        return ask(MenuCalls.CHANGE_SETTING, SettingOutcome.class, args("key", key, "value", value), actor.getName());
    }

    @Override
    public @NotNull List<ServerGroup> groups() {
        return ask(MenuCalls.GROUPS, MenuWire.listOf(ServerGroup.class));
    }

    @Override
    public boolean editGroup(@NotNull GroupEdit edit) {
        Boolean done = ask(MenuCalls.EDIT_GROUP, Boolean.class,
            args("op", edit.getOp().name(), "group", edit.getGroup(), "server", edit.getServer(), "plugin", edit.getPlugin()));
        return done;
    }

    @Override
    public @NotNull List<Component> command(@NotNull ViewerInfo viewer, @NotNull String line) {
        List<String> lines = ask(MenuCalls.COMMAND, MenuWire.listOf(String.class), args("line", line), viewer.getName());
        return MenuWire.decodeLines(lines);
    }
}
