package org.pnfolder.pnlibrary.core.menu.screens.update;

import org.pnfolder.pnlibrary.core.menu.screens.Filter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.chat.style.ChatTone;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity;
import org.pnfolder.pnlibrary.api.updates.state.UpdateSnapshot;
import org.pnfolder.pnlibrary.api.updates.state.UpdateState;
import org.pnfolder.pnlibrary.core.exceptions.Throwables;
import org.pnfolder.pnlibrary.core.menu.session.ClickType;
import org.pnfolder.pnlibrary.core.menu.ItemSpec;
import org.pnfolder.pnlibrary.core.menu.MenuRoutes;
import org.pnfolder.pnlibrary.core.menu.MenuScreens;
import org.pnfolder.pnlibrary.core.menu.session.MenuSession;
import org.pnfolder.pnlibrary.core.menu.session.MenuViewer;
import org.pnfolder.pnlibrary.core.menu.data.PluginRow;
import org.pnfolder.pnlibrary.core.menu.style.Fact;
import org.pnfolder.pnlibrary.core.menu.style.Icons;
import org.pnfolder.pnlibrary.core.menu.style.MenuConstants;
import org.pnfolder.pnlibrary.core.menu.style.MenuItems;
import org.pnfolder.pnlibrary.core.menu.style.Row;
import org.pnfolder.pnlibrary.core.menu.style.Style;
import org.pnfolder.pnlibrary.core.menu.style.Tone;
import org.pnfolder.pnlibrary.core.updates.catalog.screens.CatalogLabels;
import org.pnfolder.pnlibrary.core.updates.catalog.UpdateCatalogView;

/** Menu screens for plugin updates and problems. */
public final class UpdateScreens extends MenuScreens {
    private static final int BLOCKER_LINES = 3;
    private static final long CHECK_REOPEN_MILLIS = 3_000L;

    private final UpdateCardScreens cards;

    public UpdateScreens(@NotNull MenuSession session, @NotNull MenuRoutes routes) {
        super(session, routes);
        this.cards = new UpdateCardScreens(session, routes, this);
    }

    public void plugins(MenuViewer player, Filter filter, int page) {
        List<UpdateSnapshot> updatable = snapshots();
        List<Row> rows = new ArrayList<>();
        Set<String> known = new HashSet<>();
        for (UpdateSnapshot snapshot : updatable) {
            known.add(snapshot.getProduct().toLowerCase(Locale.ROOT));
            boolean keep = filter == Filter.ALL
                || filter == Filter.UPDATES && CatalogLabels.PENDING.contains(snapshot.getState())
                || filter == Filter.PROBLEMS && CatalogLabels.PROBLEMS.contains(snapshot.getState());
            if (keep) {
                rows.add(new Row(plate(snapshot, Style.left("открыть карточку"), Style.right("проверить")), goClick((who, click) -> {
                        if (click == ClickType.RIGHT) {
                            checkOne(who, snapshot.getProduct());
                        } else {
                            card(who, snapshot.getProduct());
                        }
                    })));
            }
        }
        if (filter == Filter.ALL) {
            rows.addAll(unmanaged(known));
        } else if (filter == Filter.PROBLEMS) {
            rows.addAll(routes.journalRows("error", who -> plugins(who, filter, page)));
        }
        list(player, filter.getTitle(), rows, page, MenuConstants.PAGE_SIZE, (who, to) -> plugins(who, filter, to),
            routes::home, "Здесь пусто");
    }

    public void card(MenuViewer player, String product) {
        cards.card(player, product);
    }

    public void checkAll(MenuViewer player) {
        player.close();
        tell(player, ChatTone.PROGRESS, "Проверяю обновления…");
        data().checkAll(player.getActor()).whenComplete((done, error) -> sync(() -> {
            if (!player.isOnline()) {
                return;
            }
            if (error != null) {
                tell(player, ChatTone.FAILURE, "Не удалось проверить обновления.", Throwables.rootMessage(error));
                return;
            }
            List<net.kyori.adventure.text.Component> news =
                UpdateCatalogView.announcement(snapshots(), MenuConstants.ROUTES, PnLibraryIdentity.SUPPORT_URL);
            if (news.isEmpty()) {
                tell(player, ChatTone.SUCCESS, "Проверка завершена: все плагины используют актуальные версии.");
            } else {
                deliver(player, news);
            }
        }));
    }

    void checkOne(MenuViewer player, String product) {
        player.close();
        tell(player, ChatTone.PROGRESS, "Проверяю обновления " + product + "…");
        data().checkOne(product, player.getActor());
        later(CHECK_REOPEN_MILLIS, () -> {
            if (player.isOnline()) {
                card(player, product);
            }
        });
    }

    void forceLibrary(MenuViewer player) {
        player.close();
        tell(player, ChatTone.PROGRESS, "Готовлю принудительное обновление pnLibrary…");
        data().forceLibrary(player.getActor()).whenComplete((version, error) -> sync(() -> {
            if (!player.isOnline()) {
                return;
            }
            if (error == null) {
                tell(player, ChatTone.SUCCESS, "Обновление pnLibrary подготовлено.",
                    "Версия " + version + " применится после перезапуска.");
            } else {
                tell(player, ChatTone.FAILURE, "Не удалось подготовить обновление pnLibrary.", Throwables.rootMessage(error));
            }
        }));
    }

    public void everyone(MenuViewer player, String done, Function<UpdateSnapshot, CompletionStage<?>> change) {
        List<CompletableFuture<?>> stages = new ArrayList<>();
        for (UpdateSnapshot snapshot : snapshots()) {
            stages.add(change.apply(snapshot).toCompletableFuture());
        }
        CompletableFuture.allOf(stages.toArray(new CompletableFuture<?>[0])).whenComplete((result, error) -> sync(() -> {
            if (error != null) {
                tell(player, ChatTone.FAILURE, "Не всё удалось изменить.", Throwables.rootMessage(error));
            } else {
                tell(player, ChatTone.SUCCESS, done);
            }
            routes.settings(player);
        }));
    }

    public CompletionStage<?> hold(UpdateSnapshot snapshot, @Nullable Duration duration, MenuViewer player) {
        return data().setHeld(snapshot.getProduct(), duration, player.getActor());
    }

    /** Plugins connected to the library that have no update registration: they are listed so none is missing. */
    private List<Row> unmanaged(Set<String> known) {
        List<PluginRow> missing = new ArrayList<>();
        for (PluginRow plugin : data().plugins()) {
            if (!known.contains(plugin.getName().toLowerCase(Locale.ROOT))) {
                missing.add(plugin);
            }
        }
        Collections.sort(missing, (a, b) -> a.getName().toLowerCase(Locale.ROOT).compareTo(b.getName().toLowerCase(Locale.ROOT)));
        List<Row> rows = new ArrayList<>();
        for (PluginRow plugin : missing) {
            List<Fact> parameters = new ArrayList<>();
            if (plugin.getVersion() != null) {
                parameters.add(new Fact("Версия", plugin.getVersion()));
            }
            parameters.add(new Fact("Модулей", String.valueOf(plugin.getModules())));
            parameters.add(new Fact("Обновления", "плагин не подключён к системе обновлений"));
            rows.add(new Row(MenuItems.icon(Icons.PLUGIN, "PAPER", plugin.getName(),
                Style.lore(Collections.emptyList(), parameters))));
        }
        return rows;
    }

    ItemSpec plate(UpdateSnapshot snapshot, String... clicks) {
        UpdateState state = snapshot.getState();
        String material = material(state);
        boolean problem = CatalogLabels.PROBLEMS.contains(state);
        List<String> problems = new ArrayList<>();
        if (problem && snapshot.getMessage() != null) {
            problems.add(Style.danger(snapshot.getMessage()));
        }
        if (state == UpdateState.BLOCKED && data().plan() != null) {
            List<String> blockers = data().plan().getBlockers();
            for (String blocker : blockers.subList(0, Math.min(BLOCKER_LINES, blockers.size()))) {
                problems.add(Style.danger(blocker));
            }
        }
        if (!problem && data().plan() != null) {
            for (String note : data().plan().getBlockers()) {
                if (note.contains(snapshot.getProduct())) {
                    problems.add(Style.danger(note));
                }
            }
        }
        List<Fact> parameters = Fact.list("Версия", snapshot.getCurrentVersion());
        if (snapshot.getLatestVersion() != null) {
            parameters.add(new Fact("Последняя", snapshot.getLatestVersion()));
        }
        parameters.add(new Fact("Канал", snapshot.getChannel().name().toLowerCase(Locale.ROOT)));
        parameters.add(new Fact("Состояние", symbol(state) + " " + CatalogLabels.stateLabel(state)));
        if (snapshot.getSupportedApi() != null) {
            parameters.add(new Fact("API pnLibrary", snapshot.getSupportedApi().getMinimum() + "–" + snapshot.getSupportedApi().getMaximum()));
        }
        return MenuItems.icon(null, material, snapshot.getProduct(),
            Style.lore(Collections.emptyList(), parameters, problems, clicks), material.equals("CLOCK") ? "WATCH" : null,
            problem ? Tone.DANGER : Tone.NORMAL);
    }

    private static String material(UpdateState state) {
        switch (state) {
            case UP_TO_DATE:
                return "EMERALD";
            case UPDATE_AVAILABLE:
                return "GOLD_INGOT";
            case UPDATE_STAGED:
            case DOWNLOADING:
            case ROLLED_BACK:
                return "DIAMOND";
            case FROZEN:
                return "CLOCK";
            case CHECKING:
                return "COMPASS";
            default:
                return "BARRIER";
        }
    }

    private static String symbol(UpdateState state) {
        switch (state) {
            case UP_TO_DATE:
                return "✔";
            case UPDATE_AVAILABLE:
                return "✦";
            case UPDATE_STAGED:
            case DOWNLOADING:
            case ROLLED_BACK:
                return "➜";
            case FROZEN:
                return "‖";
            case CHECKING:
                return "…";
            default:
                return "✖";
        }
    }
}
