package org.pnfolder.pnlibrary.core.commands.engine;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.chat.ChatKit;
import org.pnfolder.pnlibrary.api.chat.ChatPalette;
import org.pnfolder.pnlibrary.api.chat.ChatReader;
import org.pnfolder.pnlibrary.api.chat.notice.ChatNotice;
import org.pnfolder.pnlibrary.api.chat.notice.ChatTone;
import org.pnfolder.pnlibrary.api.commands.CommandSender;

/** Notices and usage lines that a command sends to its sender. */
public final class SenderNotices {
    private static final Pattern USAGE_PLACEHOLDER = Pattern.compile("<[^>]+>|\\[[^]]+]");
    private static final Pattern USAGE_COMMAND = Pattern.compile("/\\S.*");

    private SenderNotices() {
    }

    /** Sends a toned notice, with an optional second line of detail. */
    public static void notice(@NotNull CommandSender sender, @NotNull ChatTone tone, @NotNull String text, @Nullable String detail) {
        for (Component line : new ChatKit(ChatReader.of(sender)).notice(new ChatNotice(tone, text, detail))) {
            sender.sendMessage(line);
        }
    }

    /** Sends a usage line with its placeholders highlighted, clickable when the reader can click, then the help lines. */
    public static void usage(@NotNull CommandSender sender, @NotNull String text, @NotNull List<String> help) {
        ChatKit kit = new ChatKit(ChatReader.of(sender));
        Component line = kit.mark(ChatTone.WARNING);
        int from = 0;
        Matcher found = USAGE_PLACEHOLDER.matcher(text);
        while (found.find()) {
            line = line.append(kit.text(text.substring(from, found.start()), ChatPalette.TEXT))
                    .append(kit.text(found.group(), ChatPalette.ACCENT));
            from = found.end();
        }
        line = line.append(kit.text(text.substring(from), ChatPalette.TEXT));
        sender.sendMessage(suggestable(kit, line, text));
        for (String entry : help) {
            sender.sendMessage(kit.text("    " + entry, ChatPalette.LABEL));
        }
    }

    private static Component suggestable(ChatKit kit, Component line, String text) {
        Matcher command = USAGE_COMMAND.matcher(text);
        if (!command.find() || !kit.getReader().isInteractive()) {
            return line;
        }
        String typed = command.group();
        int angle = typed.indexOf('<');
        String name = (angle < 0 ? typed : typed.substring(0, angle)).replaceAll("\\s+$", "");
        return line.clickEvent(ClickEvent.suggestCommand(name + " "))
                .hoverEvent(kit.tooltip(kit.text("Нажмите, чтобы вставить команду", ChatPalette.TEXT),
                        kit.text(name, ChatPalette.LABEL)));
    }
}
