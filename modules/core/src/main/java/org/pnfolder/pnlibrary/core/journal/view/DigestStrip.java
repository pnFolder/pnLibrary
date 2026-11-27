package org.pnfolder.pnlibrary.core.journal.view;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import org.pnfolder.pnlibrary.api.chat.ChatKit;
import org.pnfolder.pnlibrary.api.chat.style.ChatPalette;
import org.pnfolder.pnlibrary.common.text.RussianText;
import org.pnfolder.pnlibrary.common.time.DisplayZone;
import org.pnfolder.pnlibrary.core.journal.JournalLevel;
import org.pnfolder.pnlibrary.core.journal.JournalRecord;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * A strip of {@value #COLUMNS} columns over the window, oldest first: the height is how many problems fell into the
 * column, red when it holds an error, yellow when only warnings, orange for the column of the server start, and a dim
 * dot when it is quiet. Hovering names the worst columns.
 */
final class DigestStrip {
    /** The activity strip always has this many columns, whatever the window is. */
    private static final int COLUMNS = 24;

    /** Strip heights for 0, 1, 2-3, 4-7 and 8+ records in one column. */
    private static final String HEIGHTS = " ▁▃▅▇";

    /** Worst columns named in the strip's hover card. */
    private static final int WORST = 3;

    private static final DateTimeFormatter HOUR_MINUTE = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_CLOCK = DateTimeFormatter.ofPattern("dd.MM HH:mm");

    private final int[] errors = new int[COLUMNS];
    private final int[] warnings = new int[COLUMNS];
    private final int startColumn;
    private final long now;
    private final long columnMillis;
    private final int windowHours;
    private final boolean withDate;

    DigestStrip(List<JournalRecord> relevant, long now, int windowHours, long startedAtMillis) {
        long windowMillis = Duration.ofHours(windowHours).toMillis();
        this.now = now;
        this.windowHours = windowHours;
        this.columnMillis = Math.max(windowMillis / COLUMNS, 1);
        this.withDate = windowMillis > Duration.ofHours(24).toMillis();
        this.startColumn = columnOf(startedAtMillis);
        for (JournalRecord record : relevant) {
            int column = columnOf(record.getTimeMillis());
            if (record.getLevel() == JournalLevel.ERROR) {
                errors[column]++;
            } else {
                warnings[column]++;
            }
        }
    }

    private int columnOf(long millis) {
        long column = COLUMNS - 1 - (now - millis) / columnMillis;
        return (int) Math.max(0, Math.min(COLUMNS - 1, column));
    }

    private int total(int column) {
        return errors[column] + warnings[column];
    }

    /** The strip as one chat line with its hover card. */
    Component line(ChatKit kit) {
        Component line = kit.text("  ");
        for (int column = 0; column < COLUMNS; column++) {
            int total = total(column);
            int height = total == 0 ? 0 : total == 1 ? 1 : total <= 3 ? 2 : total <= 7 ? 3 : 4;
            line = line.append(kit.text(height == 0 ? "·" : String.valueOf(HEIGHTS.charAt(height)), color(column)));
        }
        return line.append(kit.text("  сейчас", ChatPalette.FAINT)).hoverEvent(tip(kit));
    }

    private net.kyori.adventure.text.format.TextColor color(int column) {
        if (total(column) == 0) {
            return ChatPalette.FAINT;
        }
        if (column == startColumn) {
            return ChatPalette.ORANGE;
        }
        return errors[column] > 0 ? ChatPalette.DANGER : ChatPalette.WARN;
    }

    private HoverEvent<Component> tip(ChatKit kit) {
        List<Component> tip = new ArrayList<>();
        tip.add(kit.text("Последние " + windowHours + " " + RussianText.plural(windowHours, "час", "часа", "часов"),
            ChatPalette.LABEL));
        tip.add(kit.blank());
        for (int column : worstColumns()) {
            long start = now - (COLUMNS - column) * columnMillis;
            Component row = kit.text(clock(start, withDate) + "  ", ChatPalette.SOFT);
            if (errors[column] > 0) {
                row = row.append(kit.text("● " + errors[column] + "  ", ChatPalette.DANGER));
            }
            if (warnings[column] > 0) {
                row = row.append(kit.text("▲ " + warnings[column], ChatPalette.WARN));
            }
            tip.add(row);
        }
        tip.add(kit.text("Остальное время тихо", ChatPalette.FAINT));
        return kit.tooltip(tip.toArray(new Component[0]));
    }

    private List<Integer> worstColumns() {
        List<Integer> busy = new ArrayList<>();
        for (int column = 0; column < COLUMNS; column++) {
            if (total(column) > 0) {
                busy.add(column);
            }
        }
        busy.sort((left, right) -> {
            int byWeight = Integer.compare(errors[right] * 1_000 + warnings[right],
                errors[left] * 1_000 + warnings[left]);
            return byWeight != 0 ? byWeight : Integer.compare(right, left);
        });
        return busy.subList(0, Math.min(WORST, busy.size()));
    }

    /** The clock of {@code millis} in the display zone, with the date when the window is longer than a day. */
    static String clock(long millis, boolean withDate) {
        return (withDate ? DATE_CLOCK : HOUR_MINUTE).withZone(DisplayZone.getZone()).format(Instant.ofEpochMilli(millis));
    }
}
