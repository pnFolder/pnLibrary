package org.pnfolder.pnlibrary.core.commands.proxy;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.audience.AudienceExtensions;
import org.pnfolder.pnlibrary.api.chat.ChatKit;
import org.pnfolder.pnlibrary.api.chat.style.ChatPalette;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.api.commands.definition.ArgumentType;
import org.pnfolder.pnlibrary.api.commands.definition.CommandBuilder;
import org.pnfolder.pnlibrary.api.network.ConnectResult;
import org.pnfolder.pnlibrary.api.network.NetworkPlayer;
import org.pnfolder.pnlibrary.api.network.NetworkProxy;
import org.pnfolder.pnlibrary.api.network.NetworkServer;
import org.pnfolder.pnlibrary.common.text.RussianText;
import org.pnfolder.pnlibrary.core.commands.engine.CommandOutput;

/**
 * The part of {@code /pn servers} that moves players between servers:
 * <pre>
 * /pn servers send &lt;player&gt; &lt;server&gt;          moves one player
 * /pn servers sendall &lt;from&gt; &lt;server&gt;         moves everyone from one server to another
 * </pre>
 */
final class PlayerSending {
    static final String PERMISSION = "pnlibrary.servers.send";

    private final NetworkProxy proxy;

    PlayerSending(@NotNull NetworkProxy proxy) {
        this.proxy = Objects.requireNonNull(proxy, "proxy");
    }

    void install(CommandBuilder root) {
        root.literal("send", send -> {
            send.permission(PERMISSION, "pnlibrary.admin");
            send.argument("player", ArgumentType.string(), player -> {
                player.suggests(context -> players());
                player.argument("target", ArgumentType.string(), target -> {
                    target.suggests(context -> servers());
                    target.executes(this::sendOne);
                });
            });
        });
        root.literal("sendall", sendAll -> {
            sendAll.permission(PERMISSION, "pnlibrary.admin");
            sendAll.argument("from", ArgumentType.string(), from -> {
                from.suggests(context -> servers());
                from.argument("target", ArgumentType.string(), target -> {
                    target.suggests(context -> servers());
                    target.executes(this::sendEveryone);
                });
            });
        });
    }

    private List<String> players() {
        List<String> names = new ArrayList<>();
        for (NetworkPlayer player : proxy.getPlayers()) {
            names.add(AudienceExtensions.name(player));
        }
        return names;
    }

    private List<String> servers() {
        List<String> names = new ArrayList<>();
        for (NetworkServer server : proxy.getServers()) {
            names.add(server.getName());
        }
        return names;
    }

    private void sendOne(CommandContext context) {
        ChatKit kit = CommandOutput.kit(context);
        NetworkPlayer player = proxy.player(context.<String>get("player"));
        NetworkServer target = proxy.server(context.<String>get("target"));
        if (player == null) {
            context.getSender().sendMessage(kit.text("Такого игрока нет в сети: " + context.<String>get("player"), ChatPalette.DANGER));
        } else if (target == null) {
            context.getSender().sendMessage(kit.text("Такого сервера нет: " + context.<String>get("target"), ChatPalette.DANGER));
        } else {
            player.connect(target).thenAccept(result -> context.getSender().sendMessage(
                    kit.text(AudienceExtensions.name(player) + ": " + describe(result, target), result.isOnTarget() ? ChatPalette.ACCENT : ChatPalette.DANGER)));
        }
    }

    private void sendEveryone(CommandContext context) {
        ChatKit kit = CommandOutput.kit(context);
        NetworkServer from = proxy.server(context.<String>get("from"));
        NetworkServer target = proxy.server(context.<String>get("target"));
        if (from == null || target == null) {
            String missing = from == null ? context.<String>get("from") : context.<String>get("target");
            context.getSender().sendMessage(kit.text("Такого сервера нет: " + missing, ChatPalette.DANGER));
        } else if (from.getName().equals(target.getName())) {
            context.getSender().sendMessage(kit.text("Это один и тот же сервер.", ChatPalette.DANGER));
        } else {
            int total = from.getPlayers().size();
            from.sendPlayers(target).thenAccept(moved -> context.getSender().sendMessage(kit.text(
                    "С " + from.getName() + " на " + target.getName() + " перемещено " + moved + " из " + total + " "
                            + RussianText.plural(total, "игрока", "игроков", "игроков") + ".",
                    moved == total ? ChatPalette.ACCENT : ChatPalette.DANGER)));
        }
    }

    private static String describe(ConnectResult result, NetworkServer target) {
        switch (result) {
            case CONNECTED:
                return "перемещён на " + target.getName() + ".";
            case ALREADY_THERE:
                return "уже на " + target.getName() + ".";
            case BUSY:
                return "уже переходит на другой сервер, повторите позже.";
            case DENIED:
                return "переход на " + target.getName() + " запрещён.";
            default:
                return "не удалось перейти на " + target.getName() + ": сервер недоступен.";
        }
    }
}
