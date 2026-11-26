package org.pnfolder.pnlibrary.core.text;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jetbrains.annotations.NotNull;

/** Shared number, date and line-wrapping formatting for chat and console pages. */
public final class CoreFormatting {
    public static final long NANOS_PER_MICRO = 1_000L;
    public static final long NANOS_PER_MILLI = 1_000_000L;
    public static final int BAR_WIDTH = 12;
    public static final int WRAP_WIDTH = 44;
    public static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMMM", new Locale("ru"));
    private static final DateTimeFormatter DAY_YEAR = DateTimeFormatter.ofPattern("d MMMM yyyy", new Locale("ru"));
    private static final int WRAP_LINES = 8;

    private CoreFormatting() {
    }

    /** The part of a class name after the last dot. */
    public static @NotNull String shortName(@NotNull String name) {
        return name.substring(name.lastIndexOf('.') + 1);
    }

    /** Nanoseconds as "x.y мс" or "x.y мкс". */
    public static @NotNull String nanosText(long nanos) {
        if (nanos >= NANOS_PER_MILLI) {
            return String.format("%.1f мс", nanos / (double) NANOS_PER_MILLI);
        }
        return String.format("%.1f мкс", nanos / (double) NANOS_PER_MICRO);
    }

    /** "Сегодня", "Вчера" or the date, with the year only when it differs from today's. */
    public static @NotNull String dayTitle(@NotNull LocalDate date, @NotNull LocalDate today) {
        if (date.equals(today)) {
            return "Сегодня";
        }
        if (date.equals(today.minusDays(1))) {
            return "Вчера";
        }
        return (date.getYear() == today.getYear() ? DAY : DAY_YEAR).format(date);
    }

    /** Wraps {@code text} to {@link #WRAP_WIDTH} columns and at most eight lines. */
    public static @NotNull List<String> wrapText(@NotNull String text) {
        return wrapText(text, WRAP_WIDTH, WRAP_LINES);
    }

    /** Wraps {@code text} to {@code width} columns and at most eight lines. */
    public static @NotNull List<String> wrapText(@NotNull String text, int width) {
        return wrapText(text, width, WRAP_LINES);
    }

    /** Wraps {@code text} to {@code width} columns, keeping {@code limit} lines and ending with "…" when cut. */
    public static @NotNull List<String> wrapText(@NotNull String text, int width, int limit) {
        List<String> lines = new ArrayList<String>();
        for (String raw : text.split("\r\n|\n|\r", -1)) {
            StringBuilder line = new StringBuilder();
            for (String word : trimWhitespace(raw).split(" ", -1)) {
                if (line.length() > 0 && line.length() + 1 + word.length() > width) {
                    lines.add(line.toString());
                    line = new StringBuilder();
                }
                String rest = word;
                while (rest.length() > width) {
                    lines.add(rest.substring(0, width));
                    rest = rest.substring(width);
                }
                if (line.length() > 0) {
                    line.append(' ');
                }
                line.append(rest);
            }
            lines.add(line.toString());
        }
        if (lines.size() <= limit) {
            return lines;
        }
        List<String> cut = new ArrayList<String>(lines.subList(0, limit));
        cut.add("…");
        return cut;
    }

    private static String trimWhitespace(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && Character.isWhitespace(value.charAt(start))) {
            start++;
        }
        while (end > start && Character.isWhitespace(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(start, end);
    }
}
