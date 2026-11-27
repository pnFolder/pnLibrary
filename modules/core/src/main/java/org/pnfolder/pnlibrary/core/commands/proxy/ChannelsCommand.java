package org.pnfolder.pnlibrary.core.commands.proxy;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.chat.ChatKit;
import org.pnfolder.pnlibrary.api.chat.style.ChatPalette;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;
import org.pnfolder.pnlibrary.core.commands.engine.CommandOutput;

/**
 * {@code /pn channels}: the plugin-message channels opened on the proxy with {@code NetworkProxy.channel}. The menu
 * page "Каналы прокси" shows the same list.
 */
public final class ChannelsCommand {
    public static final String PERMISSION = "pnlibrary.channels";

    private final Supplier<List<String>> channels;
    private final String title;
    private final String hint;

    /** The command over the channels of a proxy; {@code channels} is asked again each time the list is shown. */
    public ChannelsCommand(@NotNull Supplier<List<String>> channels) {
        this(channels, "Каналы прокси", "library.getProxy().channel(Key.key(\"плагин\", \"имя\")).listen(...)");
    }

    /** The command over the channels of a game server. */
    public static @NotNull ChannelsCommand ofServer(@NotNull Supplier<List<String>> channels) {
        return new ChannelsCommand(channels, "Каналы сервера",
                "library.getServerNetwork().channel(Key.key(\"плагин\", \"имя\")).listen(...)");
    }

    private ChannelsCommand(Supplier<List<String>> channels, String title, String hint) {
        this.channels = Objects.requireNonNull(channels, "channels");
        this.title = title;
        this.hint = hint;
    }

    public @NotNull CommandDefinition definition() {
        return CommandDefinition.command("channels", root -> {
            root.permission(PERMISSION, "pnlibrary.admin");
            root.executes(this::show);
        });
    }

    private void show(CommandContext context) {
        ChatKit kit = CommandOutput.kit(context);
        List<String> names = channels.get();
        List<Component> lines = new ArrayList<>();
        lines.add(kit.blank());
        lines.add(kit.header(title, kit.text(String.valueOf(names.size()), ChatPalette.LABEL)));
        if (names.isEmpty()) {
            lines.add(kit.note("Ни один плагин не открыл канал.", ChatPalette.FAINT));
        }
        for (String name : names) {
            lines.add(kit.text(" ✦ " + name, ChatPalette.TEXT));
        }
        lines.add(kit.note("Открыть из плагина: " + hint, ChatPalette.FAINT));
        CommandOutput.send(context, lines);
    }
}
