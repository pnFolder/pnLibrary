package org.pnfolder.pnlibrary.core.menu.screens;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.chat.notice.ChatTone;
import org.pnfolder.pnlibrary.core.link.remote.LinkNames;
import org.pnfolder.pnlibrary.core.menu.ItemSpec;
import org.pnfolder.pnlibrary.core.menu.MenuRoutes;
import org.pnfolder.pnlibrary.core.menu.MenuScreens;
import org.pnfolder.pnlibrary.core.menu.MenuSession;
import org.pnfolder.pnlibrary.core.menu.MenuViewer;
import org.pnfolder.pnlibrary.core.menu.style.Fact;
import org.pnfolder.pnlibrary.core.menu.style.Icons;
import org.pnfolder.pnlibrary.core.menu.style.MenuConstants;
import org.pnfolder.pnlibrary.core.menu.style.MenuItems;
import org.pnfolder.pnlibrary.core.menu.style.Row;
import org.pnfolder.pnlibrary.core.menu.style.Style;
import org.pnfolder.pnlibrary.core.menu.style.Tone;
import org.pnfolder.pnlibrary.core.updates.restart.ImportantNotice;
import org.pnfolder.pnlibrary.core.updates.restart.ImportantNotices;

/** The page "Важные объявления": what has to be done somewhere in the network, until it is done. */
public final class NoticeScreens extends MenuScreens {
    public NoticeScreens(@NotNull MenuSession session, @NotNull MenuRoutes routes) {
        super(session, routes);
    }

    private List<ImportantNotice> current() {
        return ImportantNotices.from(host().getLink().getNetwork(), Instant.now());
    }

    public void notices(MenuViewer player, int page) {
        List<Row> rows = new ArrayList<>();
        for (ImportantNotice notice : current()) {
            rows.add(new Row(item(notice), go(who -> open(who, notice))));
        }
        list(player, "Важные объявления", rows, page, MenuConstants.PAGE_SIZE, (who, to) -> notices(who, to), routes::home,
            "Ничего важного нет");
    }

    private static ItemSpec item(ImportantNotice notice) {
        List<Fact> facts = new ArrayList<>(Fact.list("Где", notice.getSource()));
        if (notice.getAddress() != null) {
            facts.add(new Fact("Адрес", notice.getAddress()));
        }
        return MenuItems.icon(Icons.RESTART, "BELL", notice.getSource(), Style.lore(
            Collections.singletonList(notice.getText()), facts, Style.left("открыть меню")), Tone.WARN);
    }

    private void open(MenuViewer player, ImportantNotice notice) {
        String target = notice.getSource().equals("Прокси") ? LinkNames.PROXY : notice.getSource();
        if (!host().openRemote(player, target)) {
            tell(player, ChatTone.FAILURE, "Некому передать запрос: на " + notice.getSource() + " нет игроков онлайн.");
        }
    }
}
