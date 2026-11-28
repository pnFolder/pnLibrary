package org.pnfolder.pnlibrary.core.journal.view;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import org.pnfolder.pnlibrary.api.chat.ChatKit;
import org.pnfolder.pnlibrary.api.chat.style.ChatPalette;
import org.pnfolder.pnlibrary.core.journal.JournalLevel;
import org.pnfolder.pnlibrary.core.journal.JournalRecord;

import java.util.List;

/** One event worth a look, as a clickable line of the join notice. */
final class DigestNotable {
    /** Characters of one chat line, and the least we still show of a message. */
    private static final int CHAT_WIDTH = 52;
    private static final int MIN_MESSAGE = 10;

    private DigestNotable() {
    }

    /** {@code   ● pnShop ×3 старт  Database is locked}; the reason word is dropped when plain. */
    static Component line(ChatKit kit, List<JournalRecord> group, DigestReason reason, String root) {
        JournalRecord record = group.get(0);
        TextColor color = record.getLevel() == JournalLevel.ERROR ? ChatPalette.DANGER
            : record.getLevel() == JournalLevel.WARNING ? ChatPalette.WARN : ChatPalette.INFO;
        char icon = record.getLevel() == JournalLevel.ERROR ? '●' : '▲';
        boolean plain = reason == DigestReason.PLAIN;
        String prefix = "  " + icon + " " + record.getSource() + (group.size() > 1 ? " ×" + group.size() : "")
            + (plain ? "" : " " + reason.word) + "  ";
        int budget = Math.max(CHAT_WIDTH - prefix.length(), MIN_MESSAGE);
        String message = record.getFirstLine();
        if (message.length() > budget) {
            message = message.substring(0, budget - 1) + "…";
        }
        Component line = kit.text("  " + kit.glyph(icon) + " ", color)
            .append(kit.text(record.getSource(), ChatPalette.ACCENT));
        if (group.size() > 1) {
            line = line.append(kit.text(" ×" + group.size(), ChatPalette.ORANGE));
        }
        if (!plain) {
            line = line.append(kit.text(" " + reason.word, reason.color));
        }
        return line.append(kit.text("  " + message, ChatPalette.SOFT))
            .clickEvent(ClickEvent.runCommand(root + " view " + record.getFingerprint()))
            .hoverEvent(tip(kit, group));
    }

    private static HoverEvent<Component> tip(ChatKit kit, List<JournalRecord> group) {
        JournalRecord record = group.get(0);
        String first = record.getFirstLine();
        String when = group.size() > 1
            ? group.size() + " раз, первый " + DigestStrip.clock(group.get(group.size() - 1).getTimeMillis(), false)
                + ", последний " + DigestStrip.clock(record.getTimeMillis(), false)
            : "один раз, в " + DigestStrip.clock(record.getTimeMillis(), false);
        return kit.tooltip(
            kit.text(record.getSource(), ChatPalette.ACCENT),
            kit.text(first.length() > 120 ? first.substring(0, 120) : first, ChatPalette.TEXT),
            kit.blank(),
            kit.text(when, ChatPalette.SOFT),
            kit.blank(),
            kit.text("Нажмите, чтобы открыть карточку", ChatPalette.FAINT));
    }
}
