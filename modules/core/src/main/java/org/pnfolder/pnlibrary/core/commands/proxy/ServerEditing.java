package org.pnfolder.pnlibrary.core.commands.proxy;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.chat.ChatKit;
import org.pnfolder.pnlibrary.api.chat.ChatPalette;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.api.commands.definition.ArgumentType;
import org.pnfolder.pnlibrary.api.commands.definition.CommandBuilder;
import org.pnfolder.pnlibrary.api.network.NetworkProxy;
import org.pnfolder.pnlibrary.api.network.NetworkServer;
import org.pnfolder.pnlibrary.core.commands.engine.CommandOutput;
import org.pnfolder.pnlibrary.core.network.ServerAddress;

/**
 * The part of {@code /pn servers} that changes the proxy's list of servers while it runs:
 * <pre>
 * /pn servers add &lt;name&gt; &lt;host:port&gt;     adds a server until the restart
 * /pn servers remove &lt;name&gt;              removes a server that was added that way
 * </pre>
 * The proxy's own servers cannot be removed here, the same as through {@link NetworkProxy#removeServer}.
 */
final class ServerEditing {
    static final String PERMISSION = "pnlibrary.servers.manage";

    private final NetworkProxy proxy;

    ServerEditing(@NotNull NetworkProxy proxy) {
        this.proxy = Objects.requireNonNull(proxy, "proxy");
    }

    void install(CommandBuilder root) {
        root.literal("add", add -> {
            add.permission(PERMISSION, "pnlibrary.admin");
            add.argument("name", ArgumentType.string(), name -> name.argument("address", ArgumentType.string(), address ->
                    address.executes(this::add)));
        });
        root.literal("remove", remove -> {
            remove.permission(PERMISSION, "pnlibrary.admin");
            remove.argument("name", ArgumentType.string(), name -> {
                name.suggests(context -> names());
                name.executes(this::remove);
            });
        });
    }

    private List<String> names() {
        List<String> names = new ArrayList<>();
        for (NetworkServer server : proxy.getServers()) {
            names.add(server.getName());
        }
        return names;
    }

    private void add(CommandContext context) {
        ChatKit kit = CommandOutput.kit(context);
        String name = context.<String>get("name");
        try {
            InetSocketAddress address = ServerAddress.parse(context.<String>get("address"));
            NetworkServer added = proxy.addServer(name, address);
            context.getSender().sendMessage(kit.text("Сервер " + added.getName() + " добавлен (" + address.getHostString() + ":"
                    + address.getPort() + "). Он пропадёт после перезапуска прокси.", ChatPalette.ACCENT));
        } catch (IllegalArgumentException refused) {
            context.getSender().sendMessage(kit.text(refused.getMessage(), ChatPalette.DANGER));
        }
    }

    private void remove(CommandContext context) {
        ChatKit kit = CommandOutput.kit(context);
        String name = context.<String>get("name");
        NetworkServer server = proxy.server(name);
        if (server == null) {
            context.getSender().sendMessage(kit.text("Такого сервера нет: " + name, ChatPalette.DANGER));
        } else if (proxy.removeServer(server)) {
            context.getSender().sendMessage(kit.text("Сервер " + server.getName() + " удалён. Игроков на нём это не выгоняет.",
                    ChatPalette.ACCENT));
        } else {
            context.getSender().sendMessage(kit.text("Сервер " + server.getName()
                    + " не добавлен командой или плагином: его задаёт настройка прокси, убрать его можно только там.", ChatPalette.DANGER));
        }
    }
}
