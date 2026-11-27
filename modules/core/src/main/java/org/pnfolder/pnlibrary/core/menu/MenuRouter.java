package org.pnfolder.pnlibrary.core.menu;

import java.util.Objects;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.updates.state.UpdateSnapshot;
import org.pnfolder.pnlibrary.core.commands.tasks.TaskPeriod;
import org.pnfolder.pnlibrary.core.commands.tasks.TaskState;
import org.pnfolder.pnlibrary.core.link.report.NetworkReport;
import org.pnfolder.pnlibrary.core.link.report.NetworkServer;
import org.pnfolder.pnlibrary.core.menu.data.question.EventAbout;
import org.pnfolder.pnlibrary.core.menu.screens.ActionScreens;
import org.pnfolder.pnlibrary.core.menu.screens.update.BackupScreens;
import org.pnfolder.pnlibrary.core.menu.screens.bus.BusHistoryScreens;
import org.pnfolder.pnlibrary.core.menu.screens.bus.BusScreens;
import org.pnfolder.pnlibrary.core.menu.screens.config.ConfigScreens;
import org.pnfolder.pnlibrary.core.menu.screens.Filter;
import org.pnfolder.pnlibrary.core.menu.screens.group.GroupScreens;
import org.pnfolder.pnlibrary.core.menu.screens.HistoryScreens;
import org.pnfolder.pnlibrary.core.menu.screens.HomeScreens;
import org.pnfolder.pnlibrary.core.menu.screens.DiskScreens;
import org.pnfolder.pnlibrary.core.menu.screens.JournalScreens;
import org.pnfolder.pnlibrary.core.menu.screens.LibraryScreens;
import org.pnfolder.pnlibrary.core.menu.screens.config.LibrarySettingsScreens;
import org.pnfolder.pnlibrary.core.menu.screens.ModuleScreens;
import org.pnfolder.pnlibrary.core.menu.screens.NetworkScreens;
import org.pnfolder.pnlibrary.core.menu.screens.NoticeScreens;
import org.pnfolder.pnlibrary.core.menu.screens.ChannelScreens;
import org.pnfolder.pnlibrary.core.menu.screens.PlaceholderScreens;
import org.pnfolder.pnlibrary.core.menu.screens.config.SettingsScreens;
import org.pnfolder.pnlibrary.core.menu.screens.StatsScreens;
import org.pnfolder.pnlibrary.core.menu.screens.task.TaskScreens;
import org.pnfolder.pnlibrary.core.menu.screens.update.UpdateScreens;
import org.pnfolder.pnlibrary.core.menu.style.Fact;
import org.pnfolder.pnlibrary.core.menu.style.Row;

/** Opens menu screens by route; every route is answered by the screen group that owns it. */
public final class MenuRouter implements MenuRoutes {
    private final MenuSession session;
    private final HomeScreens homeScreens;
    private final UpdateScreens updateScreens;
    private final HistoryScreens historyScreens;
    private final BackupScreens backupScreens;
    private final SettingsScreens settingsScreens;
    private final JournalScreens journalScreens;
    private final TaskScreens taskScreens;
    private final ModuleScreens moduleScreens;
    private final LibraryScreens libraryScreens;
    private final DiskScreens diskScreens;
    private final NetworkScreens networkScreens;
    private final NoticeScreens noticeScreens;
    private final GroupScreens groupScreens;
    private final StatsScreens statsScreens;
    private final PlaceholderScreens placeholderScreens;
    private final ChannelScreens channelScreens;
    private final ActionScreens actionScreens;
    private final BusScreens busScreens;
    private final BusHistoryScreens busHistoryScreens;
    private final ConfigScreens configScreens;
    private final LibrarySettingsScreens librarySettingsScreens;

    public MenuRouter(@NotNull MenuSession session) {
        this.session = Objects.requireNonNull(session, "session");
        this.homeScreens = new HomeScreens(session, this);
        this.updateScreens = new UpdateScreens(session, this);
        this.historyScreens = new HistoryScreens(session, this);
        this.backupScreens = new BackupScreens(session, this);
        this.settingsScreens = new SettingsScreens(session, this);
        this.journalScreens = new JournalScreens(session, this);
        this.taskScreens = new TaskScreens(session, this);
        this.moduleScreens = new ModuleScreens(session, this);
        this.libraryScreens = new LibraryScreens(session, this);
        this.diskScreens = new DiskScreens(session, this);
        this.networkScreens = new NetworkScreens(session, this);
        this.noticeScreens = new NoticeScreens(session, this);
        this.groupScreens = new GroupScreens(session, this);
        this.statsScreens = new StatsScreens(session, this);
        this.placeholderScreens = new PlaceholderScreens(session, this);
        this.channelScreens = new ChannelScreens(session, this);
        this.actionScreens = new ActionScreens(session, this);
        this.busScreens = new BusScreens(session, this);
        this.busHistoryScreens = new BusHistoryScreens(session, this);
        this.configScreens = new ConfigScreens(session, this);
        this.librarySettingsScreens = new LibrarySettingsScreens(session, this);
    }

    @Override
    public @Nullable EventAbout aboutOf(@NotNull String name) {
        return taskScreens.aboutOf(name);
    }

    @Override
    public void backups(@NotNull MenuViewer player, int page) {
        backupScreens.backups(player, page);
    }

    @Override
    public void busDays(@NotNull MenuViewer player, @Nullable String event, int page) {
        busHistoryScreens.busDays(player, event, page);
    }

    @Override
    public void busEvent(@NotNull MenuViewer player, @NotNull String name, @NotNull Consumer<MenuViewer> back) {
        busScreens.busEvent(player, name, back);
    }

    @Override
    public void busHome(@NotNull MenuViewer player) {
        busScreens.busHome(player);
    }

    @Override
    public void busTraces(@NotNull MenuViewer player, @Nullable String event, int page) {
        busHistoryScreens.busTraces(player, event, page);
    }

    @Override
    public void card(@NotNull MenuViewer player, @NotNull String product) {
        updateScreens.card(player, product);
    }

    @Override
    public void checkAll(@NotNull MenuViewer player) {
        updateScreens.checkAll(player);
    }

    @Override
    public void configs(@NotNull MenuViewer player, int page) {
        configScreens.configs(player, page);
    }

    @Override
    public void everyone(@NotNull MenuViewer player, @NotNull String done, @NotNull Function<UpdateSnapshot, CompletionStage<?>> change) {
        updateScreens.everyone(player, done, change);
    }

    @Override
    public void groups(@NotNull MenuViewer player, int page) {
        groupScreens.groups(player, page);
    }

    @Override
    public void history(@NotNull MenuViewer player, @Nullable String product, int page) {
        historyScreens.history(player, product, page);
    }

    @Override
    public @NotNull CompletionStage<?> hold(@NotNull UpdateSnapshot snapshot, @Nullable Duration duration, @NotNull MenuViewer player) {
        return updateScreens.hold(snapshot, duration, player);
    }

    @Override
    public void home(@NotNull MenuViewer player) {
        homeScreens.home(player);
    }

    @Override
    public void journal(@NotNull MenuViewer player, @Nullable String level, int page) {
        journalScreens.journal(player, level, page);
    }

    @Override
    public void librarySettings(@NotNull MenuViewer player, int page) {
        librarySettingsScreens.librarySettings(player, page);
    }

    @Override
    public void pickSetting(@NotNull MenuViewer player, @NotNull String key, @NotNull String filter) {
        librarySettingsScreens.pick(player, key, filter);
    }

    @Override
    public @NotNull List<Row> journalRows(@Nullable String level, @NotNull Consumer<MenuViewer> back) {
        return journalScreens.journalRows(level, back);
    }

    @Override
    public @NotNull List<Fact> labelled(@NotNull String label, @NotNull String text) {
        return taskScreens.labelled(label, text);
    }

    @Override
    public @NotNull List<NetworkServer> members(@NotNull NetworkReport map) {
        return networkScreens.members(map);
    }

    @Override
    public void modules(@NotNull MenuViewer player, @Nullable String plugin, int page, @NotNull Consumer<MenuViewer> back) {
        moduleScreens.modules(player, plugin, page, back);
    }

    @Override
    public void libraries(@NotNull MenuViewer player, @Nullable String owner, int page, @NotNull Consumer<MenuViewer> back) {
        libraryScreens.libraries(player, owner, page, back);
    }

    @Override
    public void disk(@NotNull MenuViewer player, int page, @NotNull Consumer<MenuViewer> back) {
        diskScreens.disk(player, page, back);
    }

    @Override
    public void network(@NotNull MenuViewer player, int page) {
        networkScreens.network(player, page);
    }

    @Override
    public void notices(@NotNull MenuViewer player, int page) {
        noticeScreens.notices(player, page);
    }

    @Override
    public @NotNull String ownerLine(@NotNull String name) {
        return taskScreens.ownerLine(name);
    }

    @Override
    public @NotNull List<Fact> ownerParameters(@Nullable OwnerInfo owner) {
        return taskScreens.ownerParameters(owner);
    }

    @Override
    public void channels(@NotNull MenuViewer player, int page) {
        channelScreens.channels(player, page);
    }

    @Override
    public void placeholders(@NotNull MenuViewer player, int page) {
        placeholderScreens.placeholders(player, page);
    }

    @Override
    public void actions(@NotNull MenuViewer player, int page) {
        actionScreens.actions(player, page);
    }

    @Override
    public void plugins(@NotNull MenuViewer player, @NotNull Filter filter, int page) {
        updateScreens.plugins(player, filter, page);
    }

    @Override
    public void settings(@NotNull MenuViewer player) {
        settingsScreens.settings(player);
    }

    @Override
    public void missingPlugins(@NotNull MenuViewer player, int page) {
        settingsScreens.missingPlugins(player, page);
    }

    @Override
    public void stats(@NotNull MenuViewer player) {
        statsScreens.stats(player);
    }

    @Override
    public void support(@NotNull MenuViewer player) {
        settingsScreens.support(player);
    }

    @Override
    public void tasks(@NotNull MenuViewer player, @NotNull TaskPeriod period, @NotNull TaskState state, int page) {
        taskScreens.tasks(player, period, state, page);
    }

    @Override
    public void tools(@NotNull MenuViewer player) {
        settingsScreens.tools(player);
    }

    @Override
    public @NotNull String versioned(@NotNull OwnerInfo owner) {
        return taskScreens.versioned(owner);
    }

    public void open(@NotNull MenuViewer player) {
        session.away(player, () -> home(player));
    }

    public void pick(@NotNull MenuViewer player, @NotNull String key, @NotNull String filter) {
        session.away(player, () -> pickSetting(player, key, filter));
    }
}
