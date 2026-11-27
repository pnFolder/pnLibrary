package org.pnfolder.pnlibrary.api.chat.page;

import org.jetbrains.annotations.ApiStatus;
import org.pnfolder.pnlibrary.api.chat.ChatKit;
import org.pnfolder.pnlibrary.api.chat.style.ChatPalette;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;

/** The lines of a {@code «Команды»} section. */
@ApiStatus.Internal
public final class CommandHintLines {
    /** Placeholders such as {@code <player>} or {@code [amount]} inside a command. */
    private static final Pattern PLACEHOLDER = Pattern.compile("<[^>]+>|\\[[^\\]]+]");

    private final ChatKit kit;

    public CommandHintLines(ChatKit kit) {
        this.kit = kit;
    }

    public List<Component> of(CommandHint[] hints) {
        List<Component> lines = new ArrayList<>();
        lines.add(kit.section("Команды"));
        for (CommandHint hint : hints) {
            if (hint.getCommand() != null) {
                lines.add(line(hint.getCommand(), hint.getPurpose()));
            }
        }
        return lines;
    }

    private Component line(String command, String purpose) {
        Component line = kit.text("    ");
        Matcher placeholders = PLACEHOLDER.matcher(command);
        for (String part : PLACEHOLDER.split(command, -1)) {
            line = line.append(kit.text(part, ChatPalette.TEXT));
            if (placeholders.find()) {
                line = line.append(kit.text(placeholders.group(), ChatPalette.ACCENT));
            }
        }
        line = line.append(kit.text(" " + kit.glyph('—') + " ", ChatPalette.FAINT))
            .append(kit.text(purpose, ChatPalette.LABEL));
        if (!kit.getReader().isInteractive()) {
            return line;
        }
        int bracket = command.indexOf('<');
        String typed = (bracket < 0 ? command : command.substring(0, bracket)).replaceAll("\\s+$", "") + " ";
        return line.clickEvent(ClickEvent.suggestCommand(typed)).hoverEvent(kit.tooltip(
            kit.text("Нажмите, чтобы вставить команду", ChatPalette.TEXT), kit.text(typed.trim(), ChatPalette.LABEL)));
    }
}
