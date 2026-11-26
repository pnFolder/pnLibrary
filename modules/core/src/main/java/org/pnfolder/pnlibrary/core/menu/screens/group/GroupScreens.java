package org.pnfolder.pnlibrary.core.menu.screens.group;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.chat.notice.ChatTone;
import org.pnfolder.pnlibrary.core.link.ServerGroup;
import org.pnfolder.pnlibrary.core.link.ServerGroupState;
import org.pnfolder.pnlibrary.core.link.ServerGroups;
import org.pnfolder.pnlibrary.core.link.remote.RemoteAction;
import org.pnfolder.pnlibrary.core.link.report.NetworkReport;
import org.pnfolder.pnlibrary.core.menu.ClickType;
import org.pnfolder.pnlibrary.core.menu.ItemSpec;
import org.pnfolder.pnlibrary.core.menu.MenuBuilder;
import org.pnfolder.pnlibrary.core.menu.MenuRoutes;
import org.pnfolder.pnlibrary.core.menu.MenuScreens;
import org.pnfolder.pnlibrary.core.menu.MenuSession;
import org.pnfolder.pnlibrary.core.menu.MenuViewer;
import org.pnfolder.pnlibrary.core.menu.data.GroupEdit;
import org.pnfolder.pnlibrary.core.menu.data.GroupOp;
import org.pnfolder.pnlibrary.core.menu.style.Fact;
import org.pnfolder.pnlibrary.core.menu.style.Icons;
import org.pnfolder.pnlibrary.core.menu.style.MenuConstants;
import org.pnfolder.pnlibrary.core.menu.style.MenuItems;
import org.pnfolder.pnlibrary.core.menu.style.Row;
import org.pnfolder.pnlibrary.core.menu.style.Style;
import org.pnfolder.pnlibrary.core.menu.style.Tone;

/** Menu screens for server groups and their members. */
public final class GroupScreens extends MenuScreens {
    static final int NETWORK_UPDATE = 51;
    static final int GROUP_ADD = 46;
    static final int GROUP_PLUGINS = 47;
    static final int GROUP_DELETE = 52;

    private final ServerGroups comparer = new ServerGroups(() -> null);
    private final GroupRemote remote;
    private final GroupPickScreens picks;

    public GroupScreens(@NotNull MenuSession session, @NotNull MenuRoutes routes) {
        super(session, routes);
        this.remote = new GroupRemote(session);
        this.picks = new GroupPickScreens(session, routes, remote, this);
    }

    List<ServerGroupState> compare(ServerGroup group, NetworkReport map) {
        return comparer.compare(group, routes.members(map));
    }

    public void groups(MenuViewer player, int page) {
        remote.withGroups(player, () -> routes.home(player), (all, map) -> {
            List<Row> rows = new ArrayList<>();
            for (ServerGroup group : all) {
                int bad = 0;
                for (ServerGroupState state : compare(group, map)) {
                    bad += state.getOk() ? 0 : 1;
                }
                String material = group.getServers().isEmpty() ? "CHEST" : bad == 0 ? "EMERALD_BLOCK" : "REDSTONE_BLOCK";
                List<Fact> parameters = Fact.list("Серверов", String.valueOf(group.getServers().size()),
                    "Плагинов", String.valueOf(group.getPlugins().size()),
                    "Состояние", bad == 0 ? "✔ в порядке" : "✖ требуют внимания: " + bad);
                rows.add(new Row(MenuItems.icon(Icons.OVERVIEW, material, group.getName(),
                    Style.lore(Collections.emptyList(), parameters, Style.left("открыть"))),
                    go(who -> group(who, group.getName()))));
            }
            list(player, "Группы", rows, page, MenuConstants.PAGE_SIZE, (who, to) -> groups(who, to),
                who -> routes.network(who, 0), "Групп нет", builder -> {
                    ItemSpec create = MenuItems.icon(Icons.PLUGIN, "WRITABLE_BOOK", "Новая группа", Style.lore(
                        java.util.Arrays.asList("Группа — набор серверов с общим списком плагинов.",
                            "Имя пишется в чате: любое, без пробелов."), Collections.emptyList(), Style.left("создать")),
                        Tone.GOOD);
                    builder.button(GROUP_ADD, create, go(who ->
                        suggestCommand(who, "Назовите группу и нажмите Enter:", "/pnproxy groups create ")));
                });
        });
    }

    void group(MenuViewer player, String name) {
        remote.withGroups(player, () -> routes.home(player), (all, map) -> {
            ServerGroup group = find(all, name);
            if (group == null) {
                groups(player, 0);
                return;
            }
            List<ServerGroupState> states = compare(group, map);
            List<Row> rows = new ArrayList<>();
            for (ServerGroupState state : states) {
                rows.add(serverRow(name, state));
            }
            list(player, "Группа " + group.getName(), rows, 0, MenuConstants.PAGE_SIZE, (who, to) -> group(who, name),
                who -> groups(who, 0), "Серверов нет", builder -> groupButtons(builder, name, group, states));
        });
    }

    static ServerGroup find(List<ServerGroup> all, String name) {
        for (ServerGroup group : all) {
            if (group.getName().equals(name)) {
                return group;
            }
        }
        return null;
    }

    private Row serverRow(String name, ServerGroupState state) {
        List<Fact> parameters = new ArrayList<>();
        parameters.add(new Fact("На связи", state.getKnown() != null && state.getKnown().getOnline() ? "✔ да" : "✖ нет"));
        parameters.add(new Fact("Свои настройки", state.getCustomized() ? "есть" : "нет, как у всех"));
        if (!state.getMissing().isEmpty()) {
            parameters.add(new Fact("Нет плагинов", String.join(", ", state.getMissing())));
        }
        if (!state.getOutdated().isEmpty()) {
            parameters.add(new Fact("Можно обновить", String.join(", ", state.getOutdated())));
        }
        List<String> lore = Style.lore(Collections.emptyList(), parameters, Style.left("настроить сервер"),
            Style.right("убрать из группы"));
        return new Row(MenuItems.icon(Icons.OVERVIEW, state.getOk() ? "EMERALD_BLOCK" : "REDSTONE_BLOCK",
            ServerGroups.label(state.getServer()), lore, state.getKnown() == null ? Tone.MUTED : Tone.NORMAL),
            goClick((who, how) -> {
                if (how == ClickType.RIGHT) {
                    remote.edit(who, new GroupEdit(GroupOp.REMOVE_SERVER, name, state.getServer()), () -> group(who, name));
                } else {
                    picks.groupServer(who, name, state.getServer());
                }
            }));
    }

    private void groupButtons(MenuBuilder builder, String name, ServerGroup group, List<ServerGroupState> states) {
        ItemSpec check = MenuItems.icon(Icons.CHECK, "COMPASS", "Проверить всех", Style.lore(java.util.Arrays.asList(
            "Серверы группы проверят, вышли ли обновления.", "Ничего не скачивается."), Collections.emptyList(),
            Style.left("проверить")));
        builder.button(MenuConstants.NETWORK_CHECK, check, go(who ->
            allowed(who, RemoteAction.CHECK.getPermission(), () -> askGroup(who, states, RemoteAction.CHECK))));
        ItemSpec update = MenuItems.icon(Icons.PLUGIN, "GOLD_INGOT", "Обновить всех", Style.lore(java.util.Arrays.asList(
            "Серверы группы скачают найденные обновления.", "Они применятся после перезапуска."), Collections.emptyList(),
            Style.left("скачать")));
        builder.button(NETWORK_UPDATE, update, go(who ->
            allowed(who, RemoteAction.UPDATE.getPermission(), () -> askGroup(who, states, RemoteAction.UPDATE))));
        ItemSpec servers = MenuItems.icon(Icons.OVERVIEW, "LIME_DYE", "Добавить сервер", Style.lore(java.util.Arrays.asList(
            "Добавляет сервер или прокси в группу.", "Сервер может состоять в нескольких группах."),
            Collections.emptyList(), Style.left("выбрать")), Tone.GOOD);
        builder.button(GROUP_ADD, servers, go(who -> picks.pickServer(who, name, 0)));
        ItemSpec plugins = MenuItems.icon(Icons.PLUGIN, "BOOKSHELF", "Плагины группы", Style.lore(java.util.Arrays.asList(
            "Плагины, нужные каждому серверу группы: " + group.getPlugins().size() + ".", "Если плагина нет, сервер будет в красном."),
            Collections.emptyList(), Style.left("открыть")));
        builder.button(GROUP_PLUGINS, plugins, go(who -> picks.groupPlugins(who, name, 0)));
        ItemSpec delete = MenuItems.icon(Icons.PROBLEMS, "TNT", "Удалить группу", Style.lore(java.util.Arrays.asList(
            "Удаляется только список группы.", "Серверы и их плагины не трогаются."), Collections.emptyList(),
            Style.shiftLeft("удалить")), Tone.DANGER);
        builder.button(GROUP_DELETE, delete, goClick((who, how) -> {
            if (how == ClickType.SHIFT_LEFT) {
                remote.edit(who, new GroupEdit(GroupOp.DELETE, name), () -> groups(who, 0));
            } else {
                tell(who, ChatTone.PROGRESS, "Чтобы удалить группу, нажмите с Shift.");
            }
        }));
    }

    private void askGroup(MenuViewer player, List<ServerGroupState> states, RemoteAction action) {
        int sent = 0;
        for (ServerGroupState state : states) {
            if (state.getServer().equals(ServerGroups.PROXY)) {
                continue;
            }
            List<String> products = new ArrayList<>();
            if (action == RemoteAction.UPDATE) {
                products.addAll(state.getOutdated());
            } else {
                products.add(null);
            }
            for (String product : products) {
                String label = product == null ? ServerGroups.label(state.getServer())
                    : ServerGroups.label(state.getServer()) + " · " + product;
                String error = host().getLink().getControl().send(state.getServer(), action, product, player.getName(),
                    result -> sync(() -> {
                        if (player.isOnline()) {
                            tell(player, result.getOk() ? ChatTone.SUCCESS : ChatTone.FAILURE, label + ": " + result.getText());
                        }
                    }));
                if (error == null) {
                    sent++;
                } else {
                    tell(player, ChatTone.FAILURE, label + ": " + error);
                }
            }
        }
        tell(player, ChatTone.PROGRESS, sent == 0 ? "Делать нечего." : "Запросов отправлено: " + sent + ".");
    }
}
