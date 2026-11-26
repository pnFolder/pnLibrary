package org.pnfolder.pnlibrary.core.journal.view;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.chat.ChatKit;
import org.pnfolder.pnlibrary.api.chat.ChatPalette;
import org.pnfolder.pnlibrary.core.journal.JournalLevel;
import org.pnfolder.pnlibrary.core.journal.JournalRecord;
import org.pnfolder.pnlibrary.core.text.CoreFormatting;

import java.util.ArrayList;
import java.util.List;

/** One event of the journal as a block of chat lines. */
final class JournalRows {
    private JournalRows() {
    }

    /** The label that marks records of {@code level}. */
    static String levelTag(JournalLevel level) {
        switch (level) {
            case ERROR:
                return "ОШИБКА";
            case WARNING:
                return "ВНИМАНИЕ";
            default:
                return "инфо";
        }
    }

    /** The color that marks records of {@code level}. */
    static TextColor levelColor(JournalLevel level) {
        switch (level) {
            case ERROR:
                return ChatPalette.DANGER;
            case WARNING:
                return ChatPalette.WARN;
            default:
                return ChatPalette.SOFT;
        }
    }

    /** A {@code |} in the level's color; only the first line of a row has it underlined, the second one is plain. */
    private static Component levelBar(ChatKit kit, JournalLevel level, boolean underlined) {
        return kit.text("|", levelColor(level)).decoration(TextDecoration.UNDERLINED, underlined);
    }

    /** The level's own shape and color: a disc for an error, a triangle for a warning, a diamond for information. */
    private static Component levelIcon(ChatKit kit, JournalLevel level) {
        switch (level) {
            case ERROR:
                return kit.text(kit.glyph('●'), ChatPalette.DANGER);
            case WARNING:
                return kit.text(kit.glyph('▲'), ChatPalette.WARN);
            default:
                return kit.text(kit.glyph('◆'), ChatPalette.INFO);
        }
    }

    /**
     * One event as a two-line block with a colored bar on its left edge: the source, the repeat count and how long
     * ago on the first line, the message on the second. {@code group} holds the repeats, newest first.
     */
    static @NotNull Component row(@NotNull ChatKit kit, @NotNull List<JournalRecord> group, boolean full,
                                  @NotNull String root) {
        JournalRecord record = group.get(0);
        String message = record.getMessage();
        String text = !full && message.length() > JournalConstants.CHAT_CHARS
            ? message.substring(0, JournalConstants.CHAT_CHARS) + "…" : message;
        Component icon = levelIcon(kit, record.getLevel());
        Component first = kit.text(" ").append(levelBar(kit, record.getLevel(), true)).append(kit.text(" "))
            .append(icon).append(kit.text(" ")).append(kit.text(record.getSource(), ChatPalette.ACCENT));
        if (group.size() > 1) {
            first = first.append(kit.text("  ×" + group.size(), ChatPalette.ORANGE));
        }
        first = first.append(kit.text("  " + JournalTime.ago(record.getTimeMillis()),
            JournalTime.agoColor(record.getTimeMillis())));
        Component second = kit.text(" ").append(levelBar(kit, record.getLevel(), false))
            .append(kit.text("   " + text, ChatPalette.TEXT));
        return first.append(Component.newline()).append(second)
            .hoverEvent(kit.tooltip(hover(kit, group, icon).toArray(new Component[0])))
            .clickEvent(ClickEvent.runCommand(root + " view " + record.getFingerprint()));
    }

    private static List<Component> hover(ChatKit kit, List<JournalRecord> group, Component icon) {
        JournalRecord record = group.get(0);
        List<Component> hover = new ArrayList<Component>();
        hover.add(icon.append(kit.text(" " + levelTag(record.getLevel()), levelColor(record.getLevel())))
            .append(kit.dot()).append(kit.text(record.getSource(), ChatPalette.ACCENT)));
        hover.add(kit.blank());
        List<String> lines = CoreFormatting.wrapText(record.getFirstLine(), JournalConstants.HOVER_WIDTH);
        for (String line : lines.subList(0, Math.min(JournalConstants.HOVER_MESSAGE_LINES, lines.size()))) {
            hover.add(kit.text(line, ChatPalette.TEXT));
        }
        hover.add(kit.blank());
        hover.add(kit.hoverField("Последний раз", kit.text(JournalTime.fullStamp(record.getTimeMillis()),
            ChatPalette.TEXT)));
        if (group.size() > 1) {
            hover.add(kit.hoverField("Первый раз", kit.text(
                JournalTime.fullStamp(group.get(group.size() - 1).getTimeMillis()), ChatPalette.TEXT)));
            hover.add(kit.hoverField("Повторов", kit.text(String.valueOf(group.size()), ChatPalette.ORANGE)));
        }
        hover.add(kit.blank());
        hover.add(kit.text("Нажмите, чтобы открыть карточку ›", ChatPalette.ACCENT));
        return hover;
    }
}
