package org.pnfolder.pnlibrary.core.journal.view;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import org.pnfolder.pnlibrary.api.chat.ChatKit;
import org.pnfolder.pnlibrary.api.chat.style.ChatPalette;
import org.pnfolder.pnlibrary.common.time.DisplayZone;
import org.pnfolder.pnlibrary.core.journal.JournalRecord;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The occurrences of one event, newest first, grouped by day. */
final class JournalRepeats {
    private JournalRepeats() {
    }

    static List<Component> section(ChatKit kit, List<JournalRecord> group) {
        List<Component> lines = new ArrayList<>();
        ZoneId zone = DisplayZone.getZone();
        LocalDate today = LocalDate.now(zone);
        Map<LocalDate, List<JournalRecord>> days = new LinkedHashMap<>();
        for (JournalRecord record : group.subList(0, Math.min(JournalConstants.REPEAT_ROWS, group.size()))) {
            LocalDate day = Instant.ofEpochMilli(record.getTimeMillis()).atZone(zone).toLocalDate();
            List<JournalRecord> items = days.get(day);
            if (items == null) {
                items = new ArrayList<>();
                days.put(day, items);
            }
            items.add(record);
        }
        for (Map.Entry<LocalDate, List<JournalRecord>> day : days.entrySet()) {
            lines.addAll(dayRows(kit, day.getKey(), day.getValue(), today, zone));
        }
        if (group.size() > JournalConstants.REPEAT_ROWS) {
            lines.add(kit.note("Показаны последние " + JournalConstants.REPEAT_ROWS + " из " + group.size() + "."));
            lines.add(kit.blank());
        }
        return lines;
    }

    /** The heading of one day and a row for each occurrence of it. */
    private static List<Component> dayRows(ChatKit kit, LocalDate day, List<JournalRecord> items, LocalDate today,
                                           ZoneId zone) {
        List<Component> lines = new ArrayList<>();
        boolean recent = day.equals(today) || day.equals(today.minusDays(1));
        String title = day.equals(today) ? "Сегодня" : day.equals(today.minusDays(1)) ? "Вчера"
            : JournalTime.longDate(day);
        HoverEvent<Component> dayTip = kit.tooltip(
            kit.text(JournalTime.DAY_WITH_YEAR.format(day), ChatPalette.TEXT),
            kit.text(JournalTime.WEEKDAY.format(day), ChatPalette.LABEL),
            kit.blank(),
            kit.hoverField("Событий в этот день", kit.text(String.valueOf(items.size()), ChatPalette.ORANGE)));
        Component heading = kit.section(title);
        if (recent) {
            heading = heading.append(kit.text(" \u00b7 " + JournalTime.longDate(day), ChatPalette.SOFT));
        }
        lines.add(heading.append(kit.text("  \u00d7" + items.size(), ChatPalette.ORANGE)).hoverEvent(dayTip));
        for (JournalRecord item : items) {
            long millis = item.getTimeMillis();
            lines.add(kit.text("    " + JournalTime.CLOCK.withZone(zone).format(Instant.ofEpochMilli(millis)),
                ChatPalette.TEXT)
                .append(kit.text("  " + JournalTime.ago(millis), JournalTime.agoColor(millis)))
                .hoverEvent(JournalTime.momentTip(kit, millis)));
        }
        lines.add(kit.blank());
        return lines;
    }
}
