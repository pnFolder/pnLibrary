package org.pnfolder.pnlibrary.core.menu.screens;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.chat.notice.ChatTone;
import org.pnfolder.pnlibrary.core.link.ReceivedNetwork;
import org.pnfolder.pnlibrary.core.link.ServerGroups;
import org.pnfolder.pnlibrary.core.link.report.NetworkReport;
import org.pnfolder.pnlibrary.core.link.report.NetworkServer;
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

/** Menu screens describing the proxy network and its servers. */
public final class NetworkScreens extends MenuScreens {
    private static final String PROXY_LABEL = "Прокси";

    public NetworkScreens(@NotNull MenuSession session, @NotNull MenuRoutes routes) {
        super(session, routes);
    }

    public void network(MenuViewer player, int page) {
        ReceivedNetwork received = host().getLink().getNetwork();
        if (received == null) {
            routes.home(player);
            tell(player, ChatTone.INFO, "Прокси ещё не прислал карту сети.",
                "Она приходит, когда на сервере есть игрок. Попробуйте через минуту.");
            return;
        }
        NetworkReport map = received.getReport();
        List<Row> rows = new ArrayList<>();
        for (NetworkServer server : members(map, PROXY_LABEL)) {
            rows.add(new Row(serverItem(server), go(who -> openServer(who, server))));
        }
        list(player, "Сеть", rows, page, MenuConstants.PAGE_SIZE, (who, to) -> network(who, to), routes::home, "Серверов нет",
            builder -> {
                String about = "Серверы с общим набором плагинов; у каждого сервера могут быть свои отличия.";
                ItemSpec item = MenuItems.section(Icons.OVERVIEW, "BOOKSHELF", "Группы", about,
                    new Fact("Групп", String.valueOf(map.getGroups().size())));
                builder.button(MenuConstants.NETWORK_CHECK, item, go(who -> routes.groups(who, 0)));
            });
    }

    public List<NetworkServer> members(NetworkReport map) {
        return members(map, ServerGroups.PROXY);
    }

    private static List<NetworkServer> members(NetworkReport map, String proxyName) {
        List<NetworkServer> servers = new ArrayList<>();
        servers.add(new NetworkServer(proxyName, true, map.getProxy()));
        servers.addAll(map.getServers());
        return servers;
    }

    private static ItemSpec serverItem(NetworkServer server) {
        LinkReportView view = new LinkReportView(server);
        List<Fact> parameters = Fact.list(
            "Сервер", view.platform(),
            "pnLibrary", view.libraryVersion(),
            "Игроков", String.valueOf(view.players()),
            "Плагинов", String.valueOf(view.pluginCount()),
            "Обновлений", view.updates() > 0 ? "✦ " + view.updates() : "✔ нет",
            "Проблем", view.problems() > 0 ? "✖ " + view.problems() : "✔ нет",
            "На связи", server.getOnline() ? "✔ да" : "✖ нет");
        return MenuItems.icon(Icons.OVERVIEW, view.material(), server.getName(),
            Style.lore(Collections.<String>emptyList(), parameters, Style.left("открыть")),
            server.getOnline() ? Tone.NORMAL : Tone.MUTED);
    }

    private void openServer(MenuViewer player, NetworkServer server) {
        String target = server.getName().equals(PROXY_LABEL) ? LinkNames.PROXY : server.getName();
        if (!host().openRemote(player, target)) {
            tell(player, ChatTone.FAILURE, "Некому передать запрос: на " + server.getName() + " нет игроков онлайн.");
        }
    }
}
