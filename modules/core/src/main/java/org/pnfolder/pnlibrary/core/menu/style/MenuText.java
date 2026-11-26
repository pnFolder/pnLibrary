package org.pnfolder.pnlibrary.core.menu.style;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.common.time.DisplayZone;
import org.pnfolder.pnlibrary.core.text.CoreFormatting;

/** Short wordings of times and bars shared by the menu screens. */
public final class MenuText {
    private static final int HOURS_PER_DAY = 24;
    private static final int MINUTES_PER_HOUR = 60;
    private static final long SECONDS_PER_MINUTE = 60L;

    private MenuText() {
    }

    public static @NotNull String timeLeft(@NotNull Duration span) {
        long days = span.toDays();
        long hours = span.toHours() % HOURS_PER_DAY;
        long minutes = span.toMinutes() % MINUTES_PER_HOUR;
        if (days > 0) {
            return days + " д " + hours + " ч";
        }
        if (hours > 0) {
            return hours + " ч " + minutes + " мин";
        }
        return Math.max(minutes, 1) + " мин";
    }

    public static @NotNull String clockTime(@NotNull Instant moment) {
        return format(moment, "HH:mm");
    }

    public static @NotNull String clockStamp(@NotNull Instant moment) {
        return format(moment, "HH:mm:ss");
    }

    private static String format(Instant moment, String clock) {
        ZoneId zone = DisplayZone.getZone();
        String year = moment.atZone(zone).getYear() == LocalDate.now(zone).getYear() ? "" : " yyyy";
        return DateTimeFormatter.ofPattern("d MMMM" + year + ", " + clock, new Locale("ru")).withZone(zone).format(moment);
    }

    /** How long ago {@code moment} was, or how long until it: {@code только что}, {@code 5 мин назад}, {@code через 2 ч 10 мин}. */
    public static @NotNull String ago(@NotNull Instant moment) {
        Duration span = Duration.between(moment, Instant.now());
        if (span.abs().getSeconds() < SECONDS_PER_MINUTE) {
            return "только что";
        }
        return span.isNegative() ? "через " + timeLeft(span.negated()) : timeLeft(span) + " назад";
    }

    public static @NotNull String progressBar(long value, long busiest) {
        int width = (int) ((value * CoreFormatting.BAR_WIDTH + busiest - 1) / Math.max(busiest, 1));
        width = Math.max(1, Math.min(width, CoreFormatting.BAR_WIDTH));
        StringBuilder bar = new StringBuilder();
        for (int index = 0; index < width; index++) {
            bar.append('█');
        }
        return bar.toString();
    }
}
