package org.pnfolder.pnlibrary.core.menu;

import org.pnfolder.pnlibrary.core.menu.session.MenuSession;
import org.pnfolder.pnlibrary.core.menu.session.MenuViewer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.pnfolder.pnlibrary.core.menu.style.Fact;
import org.pnfolder.pnlibrary.core.menu.style.Icons;
import org.pnfolder.pnlibrary.core.menu.style.MenuConstants;
import org.pnfolder.pnlibrary.core.menu.style.MenuItems;
import org.pnfolder.pnlibrary.core.menu.style.Row;
import org.pnfolder.pnlibrary.core.menu.style.Style;
import org.pnfolder.pnlibrary.core.menu.style.Tone;

/** Draws one page of a paged list: the rows, the footer with its pager and the buttons every list shares. */
public final class MenuList {
    private static final int LIST_ROWS = 6;
    private static final int REFRESH = 49;
    private static final int CLOSE = 53;
    private static final int EMPTY_SLOT = 22;

    private final MenuSession session;

    public MenuList(MenuSession session) {
        this.session = session;
    }

    public void show(MenuViewer player, String title, List<Row> rows, int page, int perPage,
              BiConsumer<MenuViewer, Integer> reopen, Consumer<MenuViewer> back, String emptyText, int total,
              Consumer<MenuBuilder> extra) {
        int pages = Math.max((total - 1) / perPage + 1, 1);
        int current = Math.max(0, Math.min(page, pages - 1));
        List<Row> shown = total == rows.size()
            ? rows.subList(Math.min(current * perPage, rows.size()), Math.min(current * perPage + perPage, rows.size()))
            : rows.subList(0, Math.min(perPage, rows.size()));
        MenuBuilder builder = Menus.chest(MenuItems.menuTitle(title + " " + (current + 1) + "/" + pages)).rows(LIST_ROWS);
        for (int slot = 0; slot < shown.size(); slot++) {
            Row row = shown.get(slot);
            if (row.getClick() != null) {
                builder.button(slot, row.getStack(), row.getClick());
            } else {
                builder.slot(slot, row.getStack());
            }
        }
        if (total == 0) {
            builder.slot(EMPTY_SLOT, MenuItems.icon(Icons.PROBLEMS, "BARRIER", emptyText, Collections.emptyList(), Tone.MUTED));
        }
        MenuItems.footerCorners(builder);
        builder.fill(MenuItems.pane(), 0, MenuConstants.FOOTER_END);
        builder.button(MenuConstants.BACK, MenuItems.backItem(), session.go(back));
        pager(builder, current, pages, perPage, total, shown.size(), reopen);
        extra.accept(builder);
        builder.button(CLOSE, MenuItems.closeItem(), session.go(MenuViewer::close));
        session.show(player, builder.build());
    }

    private void pager(MenuBuilder builder, int current, int pages, int perPage, int total, int shown,
                       BiConsumer<MenuViewer, Integer> reopen) {
        int first = total == 0 ? 0 : current * perPage + 1;
        String range = first + "–" + (current * perPage + shown) + " из " + total;
        List<Fact> position = Fact.list("Сейчас", (current + 1) + " из " + pages);
        List<String> none = new ArrayList<>();
        if (current > 0) {
            List<String> lore = Style.lore(none, position, Style.left("на предыдущую"));
            builder.button(MenuConstants.PREVIOUS, MenuItems.icon(null, "SPECTRAL_ARROW", "◀ Страница " + current, lore, Tone.GOOD),
                session.go(who -> reopen.accept(who, current - 1)));
        } else if (pages > 1) {
            builder.slot(MenuConstants.PREVIOUS, MenuItems.icon(null, "GRAY_DYE", "Это первая страница", none, Tone.MUTED));
        }
        if (current < pages - 1) {
            List<String> lore = Style.lore(none, position, Style.left("на следующую"));
            builder.button(MenuConstants.NEXT, MenuItems.icon(null, "SPECTRAL_ARROW", "Страница " + (current + 2) + " ▶", lore, Tone.GOOD),
                session.go(who -> reopen.accept(who, current + 1)));
        } else if (pages > 1) {
            builder.slot(MenuConstants.NEXT, MenuItems.icon(null, "GRAY_DYE", "Это последняя страница", none, Tone.MUTED));
        }
        List<String> refresh = Style.lore(Collections.singletonList("Показано " + range + "."), Collections.emptyList(),
            Style.left("обновить список"));
        builder.button(REFRESH, MenuItems.icon(Icons.CHECK, "COMPASS", "Страница " + (current + 1) + " из " + pages, refresh),
            session.go(who -> reopen.accept(who, current)));
    }
}
