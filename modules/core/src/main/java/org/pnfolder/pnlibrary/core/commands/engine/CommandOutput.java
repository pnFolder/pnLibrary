package org.pnfolder.pnlibrary.core.commands.engine;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.chat.ChatKit;
import org.pnfolder.pnlibrary.api.chat.ChatPalette;
import org.pnfolder.pnlibrary.api.chat.ChatReader;
import org.pnfolder.pnlibrary.api.commands.CommandContext;

/** What every command page does with its output: build the kit for the reader and send lines to the sender. */
public final class CommandOutput {
    private CommandOutput() {
    }

    /** The chat kit that fits the sender: clickable for players, plain ASCII for the console. */
    @NotNull
    public static ChatKit kit(@NotNull CommandContext context) {
        return new ChatKit(ChatReader.of(context.getSender()));
    }

    /** Sends every line to the sender of {@code context}. */
    public static void send(@NotNull CommandContext context, @NotNull List<Component> lines) {
        for (Component line : lines) {
            context.getSender().sendMessage(line);
        }
    }

    /** Sends one plain line in {@code color}. */
    public static void say(@NotNull CommandContext context, @NotNull String text, @NotNull TextColor color) {
        context.getSender().sendMessage(Component.text(text, color));
    }

    /** Sends one plain line in the ordinary text colour. */
    public static void say(@NotNull CommandContext context, @NotNull String text) {
        say(context, text, ChatPalette.TEXT);
    }
}
