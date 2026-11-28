package org.pnfolder.pnlibrary.core.menu;

import org.pnfolder.pnlibrary.core.menu.owner.OwnerInfo;
import org.pnfolder.pnlibrary.core.menu.session.MenuViewer;
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
import org.pnfolder.pnlibrary.core.menu.screens.Filter;
import org.pnfolder.pnlibrary.core.menu.style.Fact;
import org.pnfolder.pnlibrary.core.menu.style.Row;

/** The screens that more than one part of the menu opens, so each part needs no knowledge of the others. */
public interface MenuRoutes {
    @Nullable EventAbout aboutOf(@NotNull String name);

    void backups(@NotNull MenuViewer player, int page);

    void busDays(@NotNull MenuViewer player, @Nullable String event, int page);

    void busEvent(@NotNull MenuViewer player, @NotNull String name, @NotNull Consumer<MenuViewer> back);

    void busHome(@NotNull MenuViewer player);

    void busTraces(@NotNull MenuViewer player, @Nullable String event, int page);

    void card(@NotNull MenuViewer player, @NotNull String product);

    void checkAll(@NotNull MenuViewer player);

    void configs(@NotNull MenuViewer player, int page);

    void everyone(@NotNull MenuViewer player, @NotNull String done, @NotNull Function<UpdateSnapshot, CompletionStage<?>> change);

    void groups(@NotNull MenuViewer player, int page);

    void history(@NotNull MenuViewer player, @Nullable String product, int page);

    @NotNull CompletionStage<?> hold(@NotNull UpdateSnapshot snapshot, @Nullable Duration duration, @NotNull MenuViewer player);

    void home(@NotNull MenuViewer player);

    void journal(@NotNull MenuViewer player, @Nullable String level, int page);

    void librarySettings(@NotNull MenuViewer player, int page);

    void pickSetting(@NotNull MenuViewer player, @NotNull String key, @NotNull String filter);

    @NotNull List<Row> journalRows(@Nullable String level, @NotNull Consumer<MenuViewer> back);

    @NotNull List<Fact> labelled(@NotNull String label, @NotNull String text);

    @NotNull List<NetworkServer> members(@NotNull NetworkReport map);

    void modules(@NotNull MenuViewer player, @Nullable String plugin, int page, @NotNull Consumer<MenuViewer> back);

    void libraries(@NotNull MenuViewer player, @Nullable String owner, int page, @NotNull Consumer<MenuViewer> back);

    /** What every plugin takes on the disk. */
    void disk(@NotNull MenuViewer player, int page, @NotNull Consumer<MenuViewer> back);

    void network(@NotNull MenuViewer player, int page);

    /** The page with the important announcements of the network. */
    void notices(@NotNull MenuViewer player, int page);

    @NotNull String ownerLine(@NotNull String name);

    @NotNull List<Fact> ownerParameters(@Nullable OwnerInfo owner);

    void placeholders(@NotNull MenuViewer player, int page);

    void channels(@NotNull MenuViewer player, int page);

    void actions(@NotNull MenuViewer player, int page);

    void plugins(@NotNull MenuViewer player, @NotNull Filter filter, int page);

    void settings(@NotNull MenuViewer player);

    void missingPlugins(@NotNull MenuViewer player, int page);

    void stats(@NotNull MenuViewer player);

    void support(@NotNull MenuViewer player);

    void tasks(@NotNull MenuViewer player, @NotNull TaskPeriod period, @NotNull TaskState state, int page);

    void tools(@NotNull MenuViewer player);

    @NotNull String versioned(@NotNull OwnerInfo owner);
}
