package org.pnfolder.pnlibrary.core.commands.proxy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.audience.AudienceExtensions;
import org.pnfolder.pnlibrary.api.chat.ChatKit;
import org.pnfolder.pnlibrary.api.chat.ChatPalette;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.api.commands.definition.ArgumentType;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;
import org.pnfolder.pnlibrary.api.commands.definition.CommandNodeBuilder;
import org.pnfolder.pnlibrary.api.updates.state.UpdateState;
import org.pnfolder.pnlibrary.common.text.RussianText;
import org.pnfolder.pnlibrary.core.commands.engine.CommandOutput;
import org.pnfolder.pnlibrary.core.commands.tasks.TaskLabels;
import org.pnfolder.pnlibrary.core.link.Link;
import org.pnfolder.pnlibrary.core.link.LinkEntry;
import org.pnfolder.pnlibrary.core.link.ReceivedNetwork;
import org.pnfolder.pnlibrary.core.link.remote.LinkNames;
import org.pnfolder.pnlibrary.core.link.remote.RemoteAction;
import org.pnfolder.pnlibrary.core.link.report.LinkPlugin;
import org.pnfolder.pnlibrary.core.link.report.LinkReport;
import org.pnfolder.pnlibrary.core.link.report.NetworkServer;
import org.pnfolder.pnlibrary.core.updates.catalog.CatalogLabels;

/**
 * {@code /pn servers}: the servers of the network, with their pnLibrary version, players and plugins, and the update
 * controls that can be sent to them through the link. Nothing but those controls can be sent: no commands.
 *
 * <pre>
 * /pn servers                                  the list of servers; click a row to open it
 * /pn servers &lt;name&gt;                          one server: platform, version, every plugin and its update state, and buttons
 * /pn servers &lt;name&gt; check                    asks the server to look for updates now
 * /pn servers &lt;name&gt; update &lt;plugin&gt;         asks the server to download the update of a plugin
 * /pn servers &lt;name&gt; auto &lt;plugin&gt; on|off    turns automatic download of a plugin on or off there
 * </pre>
 *
 * On a proxy the list comes from the reports of the game servers; on a game server it comes from the map of the
 * network that the proxy sent, with the proxy itself as {@code proxy}.
 */
public final class ServersCommand {
    public static final String PERMISSION = "pnlibrary.servers";
    private static final String ROOT = "/pn servers";
    private static final Duration STALE = Duration.ofMinutes(2);

    private final Link link;
    private final Supplier<List<NetworkServer>> servers;

    /** The command over {@code servers}, which is asked again each time the list is needed. */
    public ServersCommand(@NotNull Link link, @NotNull Supplier<List<NetworkServer>> servers) {
        this.link = link;
        this.servers = servers;
    }

    /** The servers that reported to this proxy; one that has been silent for two minutes is offline. */
    @NotNull
    public static Supplier<List<NetworkServer>> fromReports(@NotNull final Link link) {
        return () -> {
            List<NetworkServer> all = new ArrayList<NetworkServer>();
            for (LinkEntry entry : link.getRegistry().all()) {
                boolean online = Duration.between(entry.getReceivedAt(), Instant.now()).compareTo(STALE) < 0;
                all.add(new NetworkServer(entry.getName(), online, entry.getReport()));
            }
            return all;
        };
    }

    /** The servers in the network map that the proxy sent to this game server, the proxy itself first. */
    @NotNull
    public static Supplier<List<NetworkServer>> fromNetworkMap(@NotNull final Link link) {
        return () -> {
            ReceivedNetwork network = link.getNetwork();
            List<NetworkServer> all = new ArrayList<NetworkServer>();
            if (network != null) {
                all.add(new NetworkServer(LinkNames.PROXY, true, network.getReport().getProxy()));
                all.addAll(network.getReport().getServers());
            }
            return all;
        };
    }

    @NotNull
    public CommandDefinition definition() {
        return CommandDefinition.command("servers", root -> {
            root.permission(PERMISSION, "pnlibrary.admin");
            root.executes(this::list);
            root.argument("server", ArgumentType.string(), server -> {
                server.suggests(context -> serverNames());
                server.executes(context -> view(context, context.<String>get("server")));
                action(server, "check", RemoteAction.CHECK);
                server.literal("update", update -> plugin(update, RemoteAction.UPDATE));
                server.literal("auto", auto -> {
                    auto.permission(RemoteAction.AUTO_ON.getPermission());
                    auto.argument("plugin", ArgumentType.string(), plugin -> {
                        plugin.suggests(context -> plugins(context.<String>get("server")));
                        plugin.literal("on", on -> on.executes(
                                context -> send(context, RemoteAction.AUTO_ON, context.<String>get("plugin"))));
                        plugin.literal("off", off -> off.executes(
                                context -> send(context, RemoteAction.AUTO_OFF, context.<String>get("plugin"))));
                    });
                });
            });
        });
    }

    private List<String> serverNames() {
        List<String> names = new ArrayList<String>();
        for (NetworkServer server : servers.get()) {
            names.add(server.getName());
        }
        return names;
    }

    private void action(CommandNodeBuilder node, String word, final RemoteAction action) {
        node.literal(word, literal -> {
            literal.permission(action.getPermission());
            literal.executes(context -> send(context, action, null));
        });
    }

    private void plugin(CommandNodeBuilder node, final RemoteAction action) {
        node.permission(action.getPermission());
        node.argument("plugin", ArgumentType.string(), plugin -> {
            plugin.suggests(context -> plugins(context.<String>get("server")));
            plugin.executes(context -> send(context, action, context.<String>get("plugin")));
        });
    }

    private NetworkServer find(String name) {
        for (NetworkServer server : servers.get()) {
            if (server.getName().equalsIgnoreCase(name)) {
                return server;
            }
        }
        return null;
    }

    private List<String> plugins(String server) {
        List<String> products = new ArrayList<String>();
        NetworkServer found = find(server);
        if (found != null) {
            for (LinkPlugin plugin : found.getReport().getPlugins()) {
                products.add(plugin.getProduct());
            }
        }
        return products;
    }

    private void send(final CommandContext context, RemoteAction action, String product) {
        final ChatKit kit = CommandOutput.kit(context);
        String name = context.<String>get("server");
        NetworkServer known = find(name);
        final String target = known == null ? name : known.getName();
        String error = link.getControl().send(target, action, product, AudienceExtensions.name(context.getSender()), result ->
                context.getSender().sendMessage(kit.text(target + ": " + result.getText(),
                        result.getOk() ? ChatPalette.ACCENT : ChatPalette.DANGER)));
        context.getSender().sendMessage(kit.text(error == null ? "Запрос отправлен на " + target + "…" : error,
                error == null ? ChatPalette.LABEL : ChatPalette.DANGER));
    }

    private void list(CommandContext context) {
        ChatKit kit = CommandOutput.kit(context);
        List<NetworkServer> all = servers.get();
        String count = all.size() + " " + RussianText.plural(all.size(), "сервер", "сервера", "серверов");
        List<Component> lines = new ArrayList<Component>();
        lines.add(kit.blank());
        lines.add(kit.header("Серверы сети", kit.text(count, ChatPalette.LABEL)));
        if (all.isEmpty()) {
            lines.add(kit.note("Пока ни один сервер не прислал отчёт.", ChatPalette.FAINT));
            lines.add(kit.note(link.getEnabled()
                    ? "Скопируйте файл link.key из папки pnLibrary прокси в такую же папку игровых серверов и перезапустите их."
                    : "Связь выключена: нет файла link.key.", ChatPalette.LABEL));
        }
        for (NetworkServer server : all) {
            lines.add(row(kit, server));
        }
        CommandOutput.send(context, lines);
    }

    private Component row(ChatKit kit, NetworkServer server) {
        LinkReport report = server.getReport();
        String players = report.getPlayers() + " " + RussianText.plural(report.getPlayers(), "игрок", "игрока", "игроков");
        int updates = 0;
        for (LinkPlugin plugin : report.getPlugins()) {
            if (plugin.getState().equals(UpdateState.UPDATE_AVAILABLE.name())) {
                updates++;
            }
        }
        Component title = kit.text(" ").append(kit.text(kit.glyph('●'), server.getOnline() ? ChatPalette.ACCENT : ChatPalette.DANGER))
                .append(kit.text(" " + server.getName(), ChatPalette.ACCENT))
                .append(kit.text(server.getOnline() ? "  на связи" : "  нет связи",
                        server.getOnline() ? ChatPalette.SOFT : ChatPalette.DANGER));
        Component facts = kit.text("    " + report.getPlatform(), ChatPalette.TEXT)
                .append(kit.dot()).append(kit.text("pnLibrary " + report.getLibraryVersion(), ChatPalette.SOFT))
                .append(kit.dot()).append(kit.text(players, ChatPalette.SOFT));
        if (updates > 0) {
            facts = facts.append(kit.dot()).append(kit.text("обновлений: " + updates, ChatPalette.ACCENT));
        }
        Component block = title.append(Component.newline()).append(facts);
        if (!kit.getReader().isInteractive()) {
            return block;
        }
        return block.hoverEvent(kit.tooltip(kit.text("Нажмите, чтобы открыть сервер ›", ChatPalette.ACCENT)))
                .clickEvent(ClickEvent.runCommand(ROOT + " " + server.getName()));
    }

    private void view(CommandContext context, String name) {
        ChatKit kit = CommandOutput.kit(context);
        NetworkServer server = find(name);
        if (server == null) {
            context.getSender().sendMessage(kit.text("Сервер " + name + " не присылал отчёт. Список: " + ROOT, ChatPalette.DANGER));
            return;
        }
        LinkReport report = server.getReport();
        List<Component> lines = new ArrayList<Component>();
        lines.add(kit.blank());
        lines.add(kit.header(server.getName(), kit.text(TaskLabels.ago(report.getSentAt()), ChatPalette.LABEL)));
        lines.add(kit.field("Сервер", kit.text(report.getPlatform(), ChatPalette.TEXT)));
        lines.add(kit.field("pnLibrary", kit.text(report.getLibraryVersion(), ChatPalette.TEXT)));
        lines.add(kit.field("Игроков", kit.text(String.valueOf(report.getPlayers()), ChatPalette.TEXT)));
        lines.add(kit.blank());
        lines.add(kit.section("Плагины с обновлениями"));
        if (report.getPlugins().isEmpty()) {
            lines.add(kit.note("Ни один плагин не подключён к обновлениям pnLibrary.", ChatPalette.FAINT));
        }
        for (LinkPlugin plugin : report.getPlugins()) {
            lines.add(pluginLine(kit, context, server, plugin));
        }
        lines.add(kit.blank());
        Component check = kit.button("[Проверить]", ROOT + " " + server.getName() + " check", "Попросить сервер проверить обновления",
                ChatPalette.ACCENT);
        Component actions = kit.actions(check, kit.button("[К списку]", ROOT, "Все серверы", ChatPalette.SOFT));
        lines.add(actions == null ? kit.blank() : actions);
        CommandOutput.send(context, lines);
    }

    private Component pluginLine(ChatKit kit, CommandContext context, NetworkServer server, LinkPlugin plugin) {
        UpdateState state = stateOf(plugin.getState());
        String label = state == null ? plugin.getState() : CatalogLabels.stateLabel(state);
        net.kyori.adventure.text.format.TextColor color = state == null ? ChatPalette.SOFT : CatalogLabels.stateColor(state);
        String version = plugin.getLatest() == null ? "v" + plugin.getVersion() : "v" + plugin.getVersion() + " » v" + plugin.getLatest();
        Component line = kit.text("  " + plugin.getProduct() + " ", ChatPalette.TEXT).append(kit.text(version, ChatPalette.SOFT))
                .append(kit.dot()).append(kit.text(label, color));
        if (plugin.getLatest() != null && kit.getReader().isInteractive()
                && AudienceExtensions.hasPermission(context.getSender(), RemoteAction.UPDATE.getPermission())) {
            line = line.append(kit.text(" ")).append(kit.button("[Обновить]",
                    ROOT + " " + server.getName() + " update " + plugin.getProduct(), "Скачать обновление на " + server.getName(),
                    ChatPalette.ACCENT));
        }
        return line;
    }

    private static UpdateState stateOf(String name) {
        try {
            return UpdateState.valueOf(name);
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }
}
