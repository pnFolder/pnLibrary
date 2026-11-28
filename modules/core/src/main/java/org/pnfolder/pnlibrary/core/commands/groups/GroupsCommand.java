package org.pnfolder.pnlibrary.core.commands.groups;

import static org.pnfolder.pnlibrary.core.commands.groups.GroupsChat.ROOT;

import java.util.Objects;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.audience.AudienceExtensions;
import org.pnfolder.pnlibrary.api.chat.ChatKit;
import org.pnfolder.pnlibrary.api.chat.ChatNotice;
import org.pnfolder.pnlibrary.api.chat.style.ChatTone;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.api.commands.definition.ArgumentType;
import org.pnfolder.pnlibrary.api.commands.definition.CommandBuilder;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;
import org.pnfolder.pnlibrary.api.commands.definition.CommandNodeBuilder;
import org.pnfolder.pnlibrary.core.link.Link;
import org.pnfolder.pnlibrary.core.link.ServerGroup;
import org.pnfolder.pnlibrary.core.link.ServerGroupState;
import org.pnfolder.pnlibrary.core.link.ServerGroups;
import org.pnfolder.pnlibrary.core.link.remote.RemoteAction;
import org.pnfolder.pnlibrary.core.link.report.LinkPlugin;
import org.pnfolder.pnlibrary.core.link.report.NetworkServer;

/**
 * {@code /pn groups} on a proxy: groups of servers and the plugins each of them should have, compared with what the
 * servers reported. A group is for answering "does every grief server have its plugins, and are they current".
 *
 * <p>Every command starts with a word and then names the group, so a group may be called anything, even {@code create}.
 * A server may be in any number of groups, and inside a group one server may differ from the rest. The proxy itself
 * is a member under the name {@code @proxy}, which no server can have.
 *
 * <pre>
 * /pn groups                                         the groups and how many of their servers are in order
 * /pn groups info &lt;group&gt;                           each server of the group: what is missing and what can be updated
 * /pn groups create &lt;group&gt;, delete &lt;group&gt;        makes or deletes a group
 * /pn groups join|leave &lt;group&gt; &lt;server&gt;           puts a server in the group or takes it out
 * /pn groups require|drop &lt;group&gt; &lt;plugin&gt;         every server of the group has the plugin, or no longer has to
 * /pn groups server &lt;group&gt; &lt;server&gt;             what the group asks of one server and how it differs from the rest
 * /pn groups skip|extra &lt;group&gt; &lt;server&gt; &lt;plugin&gt;  this server does not need a plugin, or needs one more
 * /pn groups reset &lt;group&gt; &lt;server&gt;              this server goes back to what the rest have
 * /pn groups of &lt;server&gt;                            the groups a server is in
 * /pn groups check|update &lt;group&gt;                   every server of the group looks for updates, or downloads them
 * </pre>
 */
public final class GroupsCommand {
    public static final String PERMISSION = "pnlibrary.groups";

    private final Link link;
    private final Supplier<List<NetworkServer>> servers;
    private final ServerGroups groups;
    private final GroupsPages pages;

    /** The command over {@code servers}, which is asked again each time the servers are compared. */
    public GroupsCommand(@NotNull Link link, @NotNull Supplier<List<NetworkServer>> servers) {
        this.link = Objects.requireNonNull(link, "link");
        this.servers = Objects.requireNonNull(servers, "servers");
        this.groups = link.groups();
        this.pages = new GroupsPages(groups, servers);
    }

    @NotNull
    public CommandDefinition definition() {
        return CommandDefinition.command("groups", root -> {
            root.permission(PERMISSION, "pnlibrary.admin");
            root.executes(pages::list);
            root.literal("create", create -> {
                create.description("Создаёт пустую группу серверов. Имя любое, до 32 символов, без пробелов.");
                create.argument("group", ArgumentType.string(), group -> group.executes(context -> create(context, context.<String>get("group"))));
            });
            editWords(root);
            overrideWords(root);
            root.literal("of", of -> {
                of.description("В каких группах состоит сервер и какие плагины они от него требуют.");
                of.argument("server", ArgumentType.string(), server -> {
                    server.suggests(context -> serverNames());
                    server.executes(context -> pages.of(context, context.<String>get("server")));
                });
            });
        });
    }

    private void editWords(CommandBuilder root) {
        withGroup(root, "delete", "Удаляет группу. Серверы и плагины не трогает, пропадает только список.",
                group -> group.executes(context -> delete(context, context.<String>get("group"))));
        withGroup(root, "info", "Каждый сервер группы: чего ему не хватает и что можно обновить.",
                group -> group.executes(context -> pages.view(context, context.<String>get("group"))));
        withGroup(root, "join", "Добавляет сервер в группу. Сервер может быть в нескольких группах; прокси добавляется как @proxy.",
                group -> valued(group, true, ServerGroup::addServer));
        withGroup(root, "leave", "Убирает сервер из группы вместе с его особыми настройками.",
                group -> valued(group, true, ServerGroup::removeServer));
        withGroup(root, "require", "Плагин, который нужен каждому серверу группы. Если его нет, группа покажет это в списке.",
                group -> valued(group, false, ServerGroup::addPlugin));
        withGroup(root, "drop", "Плагин больше не обязателен для серверов группы.",
                group -> valued(group, false, ServerGroup::removePlugin));
        withGroup(root, "check", "Каждый сервер группы проверит обновления своих плагинов.", group -> {
            group.permission(RemoteAction.CHECK.getPermission());
            group.executes(context -> ask(context, RemoteAction.CHECK));
        });
        withGroup(root, "update", "Каждый сервер группы скачает доступные обновления плагинов группы.", group -> {
            group.permission(RemoteAction.UPDATE.getPermission());
            group.executes(context -> ask(context, RemoteAction.UPDATE));
        });
    }

    private void overrideWords(CommandBuilder root) {
        withGroup(root, "server", "Показывает, что группа требует от одного сервера и чем он отличается от остальных.",
                group -> withServer(group, server -> server.executes(
                        context -> pages.viewServer(context, context.<String>get("group"), context.<String>get("server")))));
        withGroup(root, "skip", "Этому серверу плагин группы не нужен; у остальных серверов группы он остаётся.",
                group -> withServer(group, server -> override(server, false)));
        withGroup(root, "extra", "Этому серверу нужен ещё один плагин, которого нет у остальных серверов группы.",
                group -> withServer(group, server -> override(server, true)));
        withGroup(root, "reset", "Возвращает сервер к тому, что требуется от всех серверов группы.",
                group -> withServer(group, server -> server.executes(context -> {
                    String name = context.<String>get("server");
                    String done = "Сервер " + ServerGroups.label(name) + " в группе теперь как все.";
                    say(context, groups.edit(context.<String>get("group"), existing -> existing.reset(name)), done);
                })));
    }

    private void withGroup(CommandBuilder root, String word, final String about, final Consumer<CommandNodeBuilder> inside) {
        root.literal(word, node -> {
            node.description(about);
            node.argument("group", ArgumentType.string(), group -> {
                group.suggests(context -> groupNames());
                inside.accept(group);
            });
        });
    }

    private void withServer(CommandNodeBuilder group, final Consumer<CommandNodeBuilder> inside) {
        group.argument("server", ArgumentType.string(), server -> {
            server.suggests(context -> {
                ServerGroup found = groups.get(context.<String>get("group"));
                return found == null ? new ArrayList<>() : new ArrayList<>(found.getServers());
            });
            inside.accept(server);
        });
    }

    private void valued(CommandNodeBuilder group, final boolean server, final BiFunction<ServerGroup, String, ServerGroup> edit) {
        final String word = server ? "server" : "plugin";
        group.argument(word, ArgumentType.string(), value -> {
            value.suggests(context -> server ? serverNames() : pluginNames(groups.get(context.<String>get("group"))));
            value.executes(context -> {
                String name = context.<String>get("group");
                say(context, groups.edit(name, existing -> edit.apply(existing, context.<String>get(word))), "Группа " + name + " изменена.");
            });
        });
    }

    private void override(CommandNodeBuilder server, final boolean extra) {
        server.argument("plugin", ArgumentType.string(), plugin -> {
            plugin.suggests(context -> pluginNames(groups.get(context.<String>get("group"))));
            plugin.executes(context -> {
                String serverName = context.<String>get("server");
                String pluginName = context.<String>get("plugin");
                String label = ServerGroups.label(serverName);
                String text = extra ? "Серверу " + label + " в группе добавлен плагин " + pluginName + "."
                        : "Серверу " + label + " в группе плагин " + pluginName + " не нужен.";
                say(context, groups.edit(context.<String>get("group"), existing -> existing.with(serverName, pluginName, extra)), text);
            });
        });
    }

    private List<String> groupNames() {
        List<String> names = new ArrayList<>();
        for (ServerGroup group : groups.all()) {
            names.add(group.getName());
        }
        return names;
    }

    private List<String> serverNames() {
        List<String> names = new ArrayList<>();
        for (NetworkServer server : servers.get()) {
            if (!server.getName().equals(ServerGroups.PROXY)) {
                names.add(server.getName());
            }
        }
        names.add(ServerGroups.PROXY);
        return names;
    }

    private List<String> pluginNames(ServerGroup group) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (NetworkServer server : servers.get()) {
            for (LinkPlugin plugin : server.getReport().getPlugins()) {
                names.add(plugin.getProduct());
            }
        }
        List<String> all = new ArrayList<>(names);
        if (group != null) {
            all.addAll(group.getPlugins());
        }
        return all;
    }

    private void say(CommandContext context, boolean saved, String done) {
        if (saved) {
            GroupsChat.notice(context, ChatTone.SUCCESS, done);
        } else {
            GroupsChat.notice(context, ChatTone.FAILURE, "Нет группы " + context.<String>get("group") + ".", "Список: " + ROOT);
        }
    }

    private void create(CommandContext context, String name) {
        if (groups.create(name)) {
            GroupsChat.notice(context, ChatTone.SUCCESS, "Группа " + name + " создана.", "Добавьте серверы: " + ROOT + " join " + name + " <сервер>");
        } else if (!ServerGroups.validName(name)) {
            GroupsChat.notice(context, ChatTone.FAILURE, "Не подходит имя группы.", "До 32 символов, без пробелов.");
        } else {
            GroupsChat.notice(context, ChatTone.FAILURE, "Группа " + name + " уже есть или файл не записался.");
        }
    }

    private void delete(CommandContext context, String name) {
        if (groups.delete(name)) {
            GroupsChat.notice(context, ChatTone.SUCCESS, "Группа " + name + " удалена.");
        } else {
            GroupsChat.notice(context, ChatTone.FAILURE, "Нет группы " + name + ".", "Список: " + ROOT);
        }
    }

    private void ask(CommandContext context, RemoteAction action) {
        String name = context.<String>get("group");
        ServerGroup group = groups.get(name);
        if (group == null) {
            GroupsChat.notice(context, ChatTone.FAILURE, "Нет группы " + name + ".", "Список: " + ROOT);
            return;
        }
        int sent = 0;
        for (ServerGroupState state : groups.compare(group, servers.get())) {
            sent += askServer(context, action, state);
        }
        if (sent == 0) {
            GroupsChat.notice(context, ChatTone.INFO, "Делать нечего.");
        } else {
            GroupsChat.notice(context, ChatTone.PROGRESS, "Запросов отправлено: " + sent + ".", "Ответы придут сюда.");
        }
    }

    /** What to ask of a server: each outdated plugin for an update, one request without a plugin otherwise. */
    private static List<String> productsFor(RemoteAction action, ServerGroupState state) {
        List<String> products = new ArrayList<>();
        if (action == RemoteAction.UPDATE) {
            products.addAll(state.getOutdated());
        } else {
            products.add(null);
        }
        return products;
    }

    /** Sends the request to one server of the group and returns how many requests went out. */
    private int askServer(final CommandContext context, RemoteAction action, ServerGroupState state) {
        List<String> products = productsFor(action, state);
        if (state.getServer().equals(ServerGroups.PROXY)) {
            if (!products.isEmpty()) {
                GroupsChat.notice(context, ChatTone.INFO, "Прокси: проверка и обновление идут своими командами.", "/pnproxy");
            }
            return 0;
        }
        final ChatKit kit = GroupsChat.kit(context);
        int sent = 0;
        for (String product : products) {
            String server = ServerGroups.label(state.getServer());
            final String label = product == null ? server : server + " · " + product;
            String error = link.getControl().send(state.getServer(), action, product, AudienceExtensions.name(context.getSender()),
                    result -> {
                        ChatTone tone = result.getOk() ? ChatTone.SUCCESS : ChatTone.FAILURE;
                        for (net.kyori.adventure.text.Component line : kit.notice(new ChatNotice(tone, label, result.getText()))) {
                            context.getSender().sendMessage(line);
                        }
                    });
            if (error == null) {
                sent++;
            } else {
                GroupsChat.notice(context, ChatTone.FAILURE, label, error);
            }
        }
        return sent;
    }
}
