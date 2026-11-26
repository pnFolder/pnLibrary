package org.pnfolder.pnlibrary.core.menu.screens;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.core.link.ServerGroup;
import org.pnfolder.pnlibrary.core.link.ServerGroups;
import org.pnfolder.pnlibrary.core.link.report.NetworkReport;
import org.pnfolder.pnlibrary.core.link.report.NetworkServer;
import org.pnfolder.pnlibrary.core.link.report.LinkPlugin;
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

/** The screens that choose a server or a plugin for a group, and the card of one server of a group. */
final class GroupPickScreens extends MenuScreens {
    private final GroupRemote remote;
    private final GroupScreens groups;

    GroupPickScreens(@NotNull MenuSession session, @NotNull MenuRoutes routes, GroupRemote remote, GroupScreens groups) {
        super(session, routes);
        this.remote = remote;
        this.groups = groups;
    }

    private static boolean has(Iterable<String> names, String wanted) {
        for (String name : names) {
            if (name.equalsIgnoreCase(wanted)) {
                return true;
            }
        }
        return false;
    }

    /** Servers of the network that are not in the group yet; a click puts one in. */
    void pickServer(MenuViewer player, String name, int page) {
        remote.withGroups(player, () -> routes.home(player), (all, map) -> {
            ServerGroup group = GroupScreens.find(all, name);
            if (group == null) {
                groups.groups(player, 0);
                return;
            }
            List<Row> rows = new ArrayList<>();
            for (NetworkServer server : routes.members(map)) {
                if (has(group.getServers(), server.getName())) {
                    continue;
                }
                List<String> others = new ArrayList<>();
                for (ServerGroup other : all) {
                    if (other != group && has(other.getServers(), server.getName())) {
                        others.add(other.getName());
                    }
                }
                ItemSpec item = MenuItems.icon(Icons.OVERVIEW, "EMERALD_BLOCK", ServerGroups.label(server.getName()), Style.lore(
                    Collections.<String>emptyList(), Fact.list("Уже в группах", others.isEmpty() ? "нет" : String.join(", ", others)),
                    Style.left("добавить в группу")));
                rows.add(new Row(item, go(who -> remote.edit(who, new GroupEdit(GroupOp.ADD_SERVER, name, server.getName()),
                    () -> groups.group(who, name)))));
            }
            list(player, "Добавить в " + name, rows, page, MenuConstants.PAGE_SIZE, (who, to) -> pickServer(who, name, to),
                who -> groups.group(who, name), "Все серверы уже в группе");
        });
    }

    /** The plugins every server of the group has; a click takes one away from all. */
    void groupPlugins(MenuViewer player, String name, int page) {
        remote.withGroups(player, () -> routes.home(player), (all, map) -> {
            ServerGroup group = GroupScreens.find(all, name);
            if (group == null) {
                groups.groups(player, 0);
                return;
            }
            List<String> plugins = sorted(group.getPlugins());
            List<Row> rows = new ArrayList<>();
            for (String plugin : plugins) {
                ItemSpec item = MenuItems.icon(Icons.PLUGIN, "BOOK", plugin, Style.lore(Collections.<String>emptyList(),
                    Collections.<Fact>emptyList(), Style.left("убрать у всех серверов группы")));
                rows.add(new Row(item, go(who -> remote.edit(who, new GroupEdit(GroupOp.REMOVE_PLUGIN, name, null, plugin),
                    () -> groupPlugins(who, name, page)))));
            }
            list(player, "Плагины " + name, rows, page, MenuConstants.PAGE_SIZE, (who, to) -> groupPlugins(who, name, to),
                who -> groups.group(who, name), "Плагинов нет", builder -> {
                    ItemSpec add = MenuItems.icon(Icons.PLUGIN, "LIME_DYE", "Добавить плагин", Style.lore(
                        java.util.Arrays.asList("Плагин станет обязательным", "для каждого сервера группы."),
                        Collections.<Fact>emptyList(), Style.left("выбрать")), Tone.GOOD);
                    builder.button(GroupScreens.GROUP_ADD, add, go(who -> pickPlugin(who, name, null, 0)));
                });
        });
    }

    private static List<String> sorted(Iterable<String> names) {
        List<String> list = new ArrayList<>();
        for (String name : names) {
            list.add(name);
        }
        Collections.sort(list, new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                return a.toLowerCase(Locale.ROOT).compareTo(b.toLowerCase(Locale.ROOT));
            }
        });
        return list;
    }

    /** The plugins the servers report, which the group or {@code server} does not ask for yet; a click asks for one. */
    private void pickPlugin(MenuViewer player, String name, String server, int page) {
        remote.withGroups(player, () -> routes.home(player), (all, map) -> {
            ServerGroup group = GroupScreens.find(all, name);
            if (group == null) {
                groups.groups(player, 0);
                return;
            }
            Set<String> have = server == null ? group.getPlugins() : group.wanted(server);
            List<String> known = sorted(knownPlugins(map));
            List<Row> rows = new ArrayList<>();
            for (String plugin : known) {
                if (has(have, plugin)) {
                    continue;
                }
                ItemSpec item = MenuItems.icon(Icons.PLUGIN, "BOOK", plugin, Style.lore(Collections.<String>emptyList(),
                    Collections.<Fact>emptyList(), Style.left(server == null ? "нужен всем" : "нужен этому серверу")));
                GroupEdit edit = server == null ? new GroupEdit(GroupOp.ADD_PLUGIN, name, null, plugin)
                    : new GroupEdit(GroupOp.NEED, name, server, plugin);
                rows.add(new Row(item, go(who -> remote.edit(who, edit, () -> {
                    if (server == null) {
                        groupPlugins(who, name, 0);
                    } else {
                        groupServer(who, name, server);
                    }
                }))));
            }
            list(player, "Добавить плагин", rows, page, MenuConstants.PAGE_SIZE, (who, to) -> pickPlugin(who, name, server, to),
                who -> {
                    if (server == null) {
                        groupPlugins(who, name, 0);
                    } else {
                        groupServer(who, name, server);
                    }
                }, "Все известные плагины уже есть");
        });
    }

    private static Set<String> knownPlugins(NetworkReport map) {
        Set<String> known = new LinkedHashSet<>();
        for (NetworkServer server : map.getServers()) {
            for (LinkPlugin plugin : server.getReport().getPlugins()) {
                known.add(plugin.getProduct());
            }
        }
        for (LinkPlugin plugin : map.getProxy().getPlugins()) {
            known.add(plugin.getProduct());
        }
        return known;
    }

    void groupServer(MenuViewer player, String name, String server) {
        remote.withGroups(player, () -> routes.home(player), (all, map) -> {
            ServerGroup group = GroupScreens.find(all, name);
            if (group == null || !has(group.getServers(), server)) {
                groups.group(player, name);
                return;
            }
            List<LinkPlugin> installed = new ArrayList<>();
            for (NetworkServer member : routes.members(map)) {
                if (member.getName().equalsIgnoreCase(server)) {
                    installed = member.getReport().getPlugins();
                    break;
                }
            }
            Set<String> names = new LinkedHashSet<>(group.getPlugins());
            names.addAll(group.wanted(server));
            List<Row> rows = new ArrayList<>();
            for (String plugin : sorted(names)) {
                boolean present = false;
                for (LinkPlugin report : installed) {
                    present |= report.getProduct().equalsIgnoreCase(plugin);
                }
                rows.add(serverPluginRow(name, server, group, plugin, present));
            }
            list(player, name + " · " + ServerGroups.label(server), rows, 0, MenuConstants.PAGE_SIZE,
                (who, to) -> groupServer(who, name, server), who -> groups.group(who, name), "У группы нет плагинов",
                builder -> serverButtons(builder, name, server));
        });
    }

    private Row serverPluginRow(String name, String server, ServerGroup group, String plugin, boolean present) {
        boolean wanted = has(group.wanted(server), plugin);
        boolean common = has(group.getPlugins(), plugin);
        String material = !wanted ? "GRAY_DYE" : present ? "EMERALD_BLOCK" : "REDSTONE_BLOCK";
        List<Fact> parameters = Fact.list("Нужен серверу", wanted ? "✔ да" : "✖ нет",
            "Откуда", !wanted ? "убран только у этого сервера" : common ? "общий для группы" : "только этому серверу",
            "На сервере", present ? "✔ стоит" : "✖ нет");
        ItemSpec item = MenuItems.icon(Icons.PLUGIN, material, plugin, Style.lore(Collections.<String>emptyList(), parameters,
            Style.left(wanted ? "не нужен этому серверу" : "нужен этому серверу")), wanted ? Tone.NORMAL : Tone.MUTED);
        GroupOp op = wanted ? GroupOp.SKIP : GroupOp.NEED;
        return new Row(item, go(who -> remote.edit(who, new GroupEdit(op, name, server, plugin), () -> groupServer(who, name, server))));
    }

    private void serverButtons(MenuBuilder builder, String name, String server) {
        ItemSpec add = MenuItems.icon(Icons.PLUGIN, "LIME_DYE", "Ещё плагин этому серверу", Style.lore(java.util.Arrays.asList(
            "Плагин нужен только этому серверу,", "остальные серверы группы его не ждут."), Collections.<Fact>emptyList(),
            Style.left("выбрать")), Tone.GOOD);
        builder.button(GroupScreens.GROUP_ADD, add, go(who -> pickPlugin(who, name, server, 0)));
        ItemSpec reset = MenuItems.icon(Icons.CHECK, "COMPASS", "Как у всех", Style.lore(java.util.Arrays.asList(
            "Сервер снова ждёт то же, что и вся группа.", "Его особые настройки удаляются."), Collections.<Fact>emptyList(),
            Style.left("сбросить")));
        builder.button(GroupScreens.GROUP_PLUGINS, reset, go(who ->
            remote.edit(who, new GroupEdit(GroupOp.RESET, name, server), () -> groupServer(who, name, server))));
    }
}
