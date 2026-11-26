package org.pnfolder.pnlibrary.core.commands.groups;

import static org.pnfolder.pnlibrary.core.commands.groups.GroupsChat.ROOT;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.pnfolder.pnlibrary.api.chat.ChatKit;
import org.pnfolder.pnlibrary.api.chat.ChatPalette;
import org.pnfolder.pnlibrary.api.chat.CommandHint;
import org.pnfolder.pnlibrary.api.chat.notice.ChatTone;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.common.text.RussianText;
import org.pnfolder.pnlibrary.core.commands.engine.CommandOutput;
import org.pnfolder.pnlibrary.core.link.ServerGroup;
import org.pnfolder.pnlibrary.core.link.ServerGroupState;
import org.pnfolder.pnlibrary.core.link.ServerGroups;
import org.pnfolder.pnlibrary.core.link.report.LinkPlugin;
import org.pnfolder.pnlibrary.core.link.report.NetworkServer;

/** The read-only pages of {@code /pn groups}: the list of groups, one group, one server in a group, and the groups of a server. */
final class GroupsPages {
    private final ServerGroups groups;
    private final Supplier<List<NetworkServer>> servers;

    GroupsPages(ServerGroups groups, Supplier<List<NetworkServer>> servers) {
        this.groups = groups;
        this.servers = servers;
    }

    void of(CommandContext context, String server) {
        ChatKit kit = GroupsChat.kit(context);
        List<Component> lines = new ArrayList<Component>();
        lines.add(kit.blank());
        lines.add(kit.header(ServerGroups.label(server), kit.text("в группах", ChatPalette.LABEL)));
        List<ServerGroup> member = groups.of(server);
        if (member.isEmpty()) {
            lines.add(kit.note("Сервер не состоит ни в одной группе.", ChatPalette.FAINT));
        }
        for (ServerGroup group : member) {
            String wanted = String.join(", ", group.wanted(server));
            Component line = kit.text(" ").append(kit.text(group.getName(), ChatPalette.ACCENT)).append(kit.dot())
                    .append(kit.text(wanted.isEmpty() ? "нет плагинов" : wanted, ChatPalette.SOFT));
            if (kit.getReader().isInteractive()) {
                line = line.hoverEvent(kit.tooltip(kit.text("Открыть сервер в группе ›", ChatPalette.ACCENT)))
                        .clickEvent(ClickEvent.runCommand(ROOT + " server " + group.getName() + " " + server));
            }
            lines.add(line);
        }
        CommandOutput.send(context, lines);
    }

    void viewServer(CommandContext context, String name, String server) {
        ChatKit kit = GroupsChat.kit(context);
        ServerGroup group = groups.get(name);
        if (group == null || !containsIgnoreCase(group.getServers(), server)) {
            GroupsChat.notice(context, ChatTone.FAILURE, "В группе " + name + " нет сервера " + ServerGroups.label(server) + ".",
                    "Добавить: " + ROOT + " join " + name + " " + server);
            return;
        }
        List<LinkPlugin> have = installedOn(server);
        Component note = kit.text(group.customized(server) ? "свои настройки" : "как у всех", ChatPalette.LABEL);
        List<Component> lines = new ArrayList<Component>();
        lines.add(kit.blank());
        lines.add(kit.header(name + " · " + ServerGroups.label(server), note));
        List<String> all = new ArrayList<String>(group.getPlugins());
        all.addAll(group.wanted(server));
        Collections.sort(all, String.CASE_INSENSITIVE_ORDER);
        if (all.isEmpty()) {
            lines.add(kit.note("У группы нет плагинов: " + ROOT + " require " + name + " <плагин>", ChatPalette.FAINT));
        }
        for (String plugin : all) {
            lines.add(pluginLine(kit, group, server, plugin, have));
        }
        lines.add(kit.blank());
        lines.addAll(kit.commandHints(
                new CommandHint(ROOT + " skip " + name + " " + server + " <плагин>", "этому серверу плагин не нужен"),
                new CommandHint(ROOT + " extra " + name + " " + server + " <плагин>", "этому серверу нужен ещё плагин"),
                new CommandHint(ROOT + " reset " + name + " " + server, "сделать как у всех")));
        CommandOutput.send(context, lines);
    }

    private List<LinkPlugin> installedOn(String server) {
        for (NetworkServer known : servers.get()) {
            if (known.getName().equalsIgnoreCase(server)) {
                return known.getReport().getPlugins();
            }
        }
        return new ArrayList<LinkPlugin>();
    }

    private static boolean containsIgnoreCase(Set<String> names, String wanted) {
        for (String name : names) {
            if (name.equalsIgnoreCase(wanted)) {
                return true;
            }
        }
        return false;
    }

    private Component pluginLine(ChatKit kit, ServerGroup group, String server, String plugin, List<LinkPlugin> have) {
        boolean wanted = containsIgnoreCase(group.wanted(server), plugin);
        boolean common = containsIgnoreCase(group.getPlugins(), plugin);
        String mark = wanted ? "нужен" : "не нужен";
        String origin = wanted && !common ? "только этому серверу" : !wanted ? "убран у этого сервера" : "общий";
        boolean installed = false;
        for (LinkPlugin installedPlugin : have) {
            installed |= installedPlugin.getProduct().equalsIgnoreCase(plugin);
        }
        return kit.text(" ").append(kit.text(plugin, ChatPalette.ACCENT)).append(kit.dot())
                .append(kit.text(mark + " (" + origin + ")", ChatPalette.SOFT)).append(kit.dot())
                .append(kit.text(installed ? "стоит" : "нет", installed ? ChatPalette.ACCENT : ChatPalette.DANGER));
    }

    void list(CommandContext context) {
        ChatKit kit = GroupsChat.kit(context);
        List<ServerGroup> all = groups.all();
        String count = all.size() + " " + RussianText.plural(all.size(), "группа", "группы", "групп");
        List<Component> lines = new ArrayList<Component>();
        lines.add(kit.blank());
        lines.add(kit.header("Группы серверов", kit.text(count, ChatPalette.LABEL)));
        if (all.isEmpty()) {
            lines.add(kit.note("Групп пока нет. Создайте: " + ROOT + " create <имя>", ChatPalette.FAINT));
        }
        for (ServerGroup group : all) {
            lines.add(groupRow(kit, group));
        }
        if (!kit.getReader().isInteractive()) {
            lines.add(kit.blank());
            lines.addAll(kit.commandHints(
                    new CommandHint(ROOT + " info <группа>", "состояние группы"),
                    new CommandHint(ROOT + " create <группа>", "новая группа"),
                    new CommandHint(ROOT + " join <группа> <сервер>", "добавить сервер"),
                    new CommandHint(ROOT + " require <группа> <плагин>", "плагин для всех серверов группы"),
                    new CommandHint(ROOT + " server <группа> <сервер>", "свои настройки одного сервера"),
                    new CommandHint(ROOT + " of <сервер>", "в каких группах сервер")));
        }
        CommandOutput.send(context, lines);
    }

    private Component groupRow(ChatKit kit, ServerGroup group) {
        int bad = 0;
        for (ServerGroupState state : groups.compare(group, servers.get())) {
            if (!state.getOk()) {
                bad++;
            }
        }
        Component verdict = group.getServers().isEmpty() ? kit.text("пусто", ChatPalette.FAINT)
                : bad == 0 ? kit.text("всё в порядке", ChatPalette.ACCENT)
                : kit.text("требуют внимания: " + bad, ChatPalette.DANGER);
        Component line = kit.text(" ").append(kit.text(kit.glyph('●'), bad == 0 ? ChatPalette.ACCENT : ChatPalette.DANGER))
                .append(kit.text(" " + group.getName(), ChatPalette.ACCENT))
                .append(kit.dot()).append(kit.text("серверов: " + group.getServers().size(), ChatPalette.SOFT))
                .append(kit.dot()).append(kit.text("плагинов: " + group.getPlugins().size(), ChatPalette.SOFT))
                .append(kit.dot()).append(verdict);
        if (kit.getReader().isInteractive()) {
            line = line.hoverEvent(kit.tooltip(kit.text("Нажмите, чтобы открыть группу ›", ChatPalette.ACCENT)))
                    .clickEvent(ClickEvent.runCommand(ROOT + " info " + group.getName()));
        }
        return line;
    }

    void view(CommandContext context, String name) {
        ChatKit kit = GroupsChat.kit(context);
        ServerGroup group = groups.get(name);
        if (group == null) {
            GroupsChat.notice(context, ChatTone.FAILURE, "Нет группы " + name + ".", "Список: " + ROOT);
            return;
        }
        List<Component> lines = new ArrayList<Component>();
        lines.add(kit.blank());
        lines.add(kit.header(group.getName(), kit.text("группа серверов", ChatPalette.LABEL)));
        String plugins = String.join(", ", group.getPlugins());
        lines.add(kit.field("Плагины", kit.text(plugins.isEmpty() ? "не заданы" : plugins, ChatPalette.TEXT)));
        lines.add(kit.blank());
        lines.add(kit.section("Серверы"));
        if (group.getServers().isEmpty()) {
            lines.add(kit.note("Серверов нет: " + ROOT + " join " + group.getName() + " <сервер>", ChatPalette.FAINT));
        }
        for (ServerGroupState state : groups.compare(group, servers.get())) {
            lines.add(serverRow(kit, group, state));
        }
        lines.add(kit.blank());
        Component actions = kit.actions(
                kit.button("[Проверить]", ROOT + " check " + group.getName(), "Каждый сервер группы проверит обновления", ChatPalette.ACCENT),
                kit.button("[Обновить]", ROOT + " update " + group.getName(), "Скачать обновления плагинов группы на всех серверах",
                        ChatPalette.ACCENT),
                kit.button("[К списку]", ROOT, "Все группы", ChatPalette.SOFT));
        lines.add(actions == null ? kit.blank() : actions);
        CommandOutput.send(context, lines);
    }

    private Component serverRow(ChatKit kit, ServerGroup group, ServerGroupState state) {
        NetworkServer known = state.getKnown();
        Component verdict;
        if (known == null) {
            verdict = kit.text("не присылал отчёт", ChatPalette.DANGER);
        } else if (!known.getOnline()) {
            verdict = kit.text("нет связи", ChatPalette.DANGER);
        } else if (state.getOk()) {
            verdict = kit.text("всё в порядке", ChatPalette.ACCENT);
        } else {
            verdict = kit.text("", ChatPalette.TEXT);
        }
        Component line = kit.text(" ").append(kit.text(kit.glyph('●'), state.getOk() ? ChatPalette.ACCENT : ChatPalette.DANGER))
                .append(kit.text(" " + ServerGroups.label(state.getServer()) + "  ", ChatPalette.ACCENT)).append(verdict);
        if (state.getCustomized()) {
            line = line.append(kit.text("свои настройки  ", ChatPalette.LABEL));
        }
        if (kit.getReader().isInteractive()) {
            line = line.hoverEvent(kit.tooltip(kit.text("Настройки сервера в группе ›", ChatPalette.ACCENT)))
                    .clickEvent(ClickEvent.runCommand(ROOT + " server " + group.getName() + " " + state.getServer()));
        }
        if (!state.getMissing().isEmpty()) {
            line = line.append(kit.text("нет: " + String.join(", ", state.getMissing()) + "  ", ChatPalette.DANGER));
        }
        if (!state.getOutdated().isEmpty()) {
            line = line.append(kit.text("можно обновить: " + String.join(", ", state.getOutdated()), ChatPalette.SOFT));
        }
        return line;
    }
}
