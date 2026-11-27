package org.pnfolder.pnlibrary.core.menu.screens;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.core.menu.MenuRoutes;
import org.pnfolder.pnlibrary.core.menu.MenuScreens;
import org.pnfolder.pnlibrary.core.menu.MenuSession;
import org.pnfolder.pnlibrary.core.menu.MenuViewer;
import org.pnfolder.pnlibrary.core.menu.style.Fact;
import org.pnfolder.pnlibrary.core.menu.style.MenuConstants;
import org.pnfolder.pnlibrary.core.menu.style.MenuItems;
import org.pnfolder.pnlibrary.core.menu.style.Row;
import org.pnfolder.pnlibrary.core.menu.style.Style;

/** The page "Каналы прокси": the plugin-message channels that plugins opened on the proxy. */
public final class ChannelScreens extends MenuScreens {
    public ChannelScreens(@NotNull MenuSession session, @NotNull MenuRoutes routes) {
        super(session, routes);
    }

    public void channels(MenuViewer player, int page) {
        List<Row> rows = new ArrayList<>();
        for (String name : data().channels()) {
            rows.add(new Row(MenuItems.icon(null, "ENDER_PEARL", name, Style.lore(
                Collections.singletonList("Слушать и отправлять: proxy.channel(\"" + name + "\")"),
                Fact.list("Принимает", "сообщения серверов"))), go(who -> { })));
        }
        list(player, "Каналы прокси", rows, page, MenuConstants.PAGE_SIZE, (who, to) -> channels(who, to), routes::home,
            "Каналов нет");
    }
}
