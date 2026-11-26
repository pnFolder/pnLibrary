package org.pnfolder.pnlibrary.core.menu.screens;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.updates.history.UpdateHistoryEntry;
import org.pnfolder.pnlibrary.api.updates.release.ProductChange;
import org.pnfolder.pnlibrary.api.updates.state.UpdateState;
import org.pnfolder.pnlibrary.core.menu.ItemSpec;
import org.pnfolder.pnlibrary.core.menu.MenuBuilder;
import org.pnfolder.pnlibrary.core.menu.MenuRoutes;
import org.pnfolder.pnlibrary.core.menu.MenuScreens;
import org.pnfolder.pnlibrary.core.menu.MenuSession;
import org.pnfolder.pnlibrary.core.menu.MenuViewer;
import org.pnfolder.pnlibrary.core.menu.Menus;
import org.pnfolder.pnlibrary.core.menu.style.Fact;
import org.pnfolder.pnlibrary.core.menu.style.Icons;
import org.pnfolder.pnlibrary.core.menu.style.MenuConstants;
import org.pnfolder.pnlibrary.core.menu.style.MenuItems;
import org.pnfolder.pnlibrary.core.menu.style.MenuText;
import org.pnfolder.pnlibrary.core.menu.style.Row;
import org.pnfolder.pnlibrary.core.menu.style.Style;
import org.pnfolder.pnlibrary.core.menu.style.Tone;
import org.pnfolder.pnlibrary.core.text.CoreFormatting;
import org.pnfolder.pnlibrary.core.updates.catalog.CatalogLabels;

/** Menu screens showing the history of updates and changes. */
public final class HistoryScreens extends MenuScreens {
    private static final int CHANGE_FIRST = 19;
    private static final int CHANGE_BUTTONS = 7;
    private static final int NOTE_LINES = 8;
    private static final int NOTE_PREVIEW_LINES = 2;

    public HistoryScreens(@NotNull MenuSession session, @NotNull MenuRoutes routes) {
        super(session, routes);
    }

    /** A page of history and how many entries there are. */
    private static final class Page {
        final int total;
        final List<UpdateHistoryEntry> entries;

        Page(int total, List<UpdateHistoryEntry> entries) {
            this.total = total;
            this.entries = entries;
        }
    }

    public void history(MenuViewer player, @Nullable String product, int page) {
        String normalized = product == null ? null : product.toLowerCase(java.util.Locale.ROOT);
        data().historyCount(normalized)
            .thenCompose(total -> data().historyPage(page + 1, normalized).thenApply(entries -> new Page(total, entries)))
            .whenComplete((result, error) -> sync(() -> {
                if (!player.isOnline()) {
                    return;
                }
                Page shown = result == null ? new Page(0, Collections.emptyList()) : result;
                Consumer<MenuViewer> back = product == null ? routes::home : who -> routes.card(who, product);
                List<Row> rows = new ArrayList<>();
                if (error == null) {
                    for (UpdateHistoryEntry entry : shown.entries) {
                        ItemSpec item = historyItem(entry, NOTE_PREVIEW_LINES, Style.left("подробности"));
                        rows.add(new Row(item, go(who -> historyEntry(who, entry, v -> history(v, product, page)))));
                    }
                }
                String title = product == null ? "История" : "История " + product;
                list(player, title, rows, page, MenuConstants.HISTORY_PAGE, (who, to) -> history(who, product, to), back,
                    "Записей пока нет", shown.total, builder -> { });
            }));
    }

    private void historyEntry(MenuViewer player, UpdateHistoryEntry entry, Consumer<MenuViewer> back) {
        MenuBuilder builder = MenuItems.menuFrame(
            Menus.chest(MenuItems.menuTitle("Запись истории")).rows(MenuConstants.DETAIL_ROWS), MenuConstants.DETAIL_ROWS);
        builder.slot(4, historyItem(entry, NOTE_LINES));
        List<ProductChange> changes = entry.getChanges();
        for (int index = 0; index < Math.min(changes.size(), CHANGE_BUTTONS); index++) {
            ProductChange change = changes.get(index);
            String product = change.getProduct().getValue();
            boolean known = data().snapshot(product) != null;
            List<Fact> facts = Fact.list("Было", String.valueOf(change.getFrom()), "Стало", String.valueOf(change.getTo()));
            List<String> lore = known
                ? Style.lore(Collections.singletonList("Версия изменилась."), facts, Style.left("открыть карточку"))
                : Style.lore(Collections.singletonList("Версия изменилась."), facts);
            ItemSpec stack = MenuItems.icon(Icons.PLUGIN, "PAPER", product, lore, known ? Tone.NORMAL : Tone.MUTED);
            if (known) {
                builder.button(CHANGE_FIRST + index, stack, go(who -> routes.card(who, product)));
            } else {
                builder.slot(CHANGE_FIRST + index, stack);
            }
        }
        builder.button(MenuConstants.DETAIL_BACK, MenuItems.backItem(), go(back));
        show(player, builder.build());
    }

    private static ItemSpec historyItem(UpdateHistoryEntry entry, int noteLines, String... clicks) {
        boolean failed = entry.getState() == UpdateState.FAILED;
        List<Fact> parameters = Fact.list("Когда", MenuText.clockTime(entry.getOccurredAt()), "Кто", entry.getActor().getName());
        for (ProductChange change : entry.getChanges()) {
            parameters.add(new Fact(change.getProduct().getValue(), change.getFrom() + " » " + change.getTo()));
        }
        List<String> note = new ArrayList<>();
        if (entry.getMessage() != null) {
            for (String line : CoreFormatting.wrapText(entry.getMessage(), CoreFormatting.WRAP_WIDTH, noteLines)) {
                note.add(failed ? Style.danger(line) : Style.description(line));
            }
        }
        String title = CatalogLabels.operationLabel(entry.getOperation()) + ": " + CatalogLabels.stateLabel(entry.getState());
        return MenuItems.icon(null, failed ? "BARRIER" : "PAPER", title,
            Style.lore(Collections.emptyList(), parameters, note, clicks), failed ? Tone.DANGER : Tone.NORMAL);
    }
}
