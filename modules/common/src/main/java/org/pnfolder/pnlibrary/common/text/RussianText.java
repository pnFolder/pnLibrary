package org.pnfolder.pnlibrary.common.text;

import org.jetbrains.annotations.NotNull;

import java.time.Duration;

/**
 * Russian number agreement and short durations. One set of abbreviations keeps the catalog, console and placeholders
 * consistent.
 */
public final class RussianText {
    private RussianText() {
    }

    /** Picks the noun form that agrees with {@code count}, e.g. {@code 1 плагин}, {@code 2 плагина}, {@code 5 плагинов}. */
    @NotNull
    public static String plural(long count, @NotNull String one, @NotNull String few, @NotNull String many) {
        long absolute = Math.abs(count);
        if (absolute % 100 >= 11 && absolute % 100 <= 14) {
            return many;
        }
        if (absolute % 10 == 1) {
            return one;
        }
        if (absolute % 10 >= 2 && absolute % 10 <= 4) {
            return few;
        }
        return many;
    }

    /** The count followed by the noun in the form that agrees with it. */
    @NotNull
    public static String counted(long count, @NotNull String one, @NotNull String few, @NotNull String many) {
        return count + " " + plural(count, one, few, many);
    }

    /** Formats {@code duration} in one unit, rounded down, never below one minute: {@code 3 д}, {@code 5 ч}, {@code 20 мин}. */
    @NotNull
    public static String period(@NotNull Duration duration) {
        if (duration.toDays() >= 1) {
            return duration.toDays() + " д";
        }
        if (duration.toHours() >= 1) {
            return duration.toHours() + " ч";
        }
        return Math.max(1L, duration.toMinutes()) + " мин";
    }

    /** Time left until something ends. Hours are kept up to two days so the value stays precise. */
    @NotNull
    public static String remaining(@NotNull Duration left) {
        long hours = left.toHours();
        if (hours >= 1 && hours <= 47) {
            return "ещё " + hours + " ч";
        }
        return "ещё " + period(left);
    }

    /** How long ago something happened, or "just now" for under a minute. */
    @NotNull
    public static String ago(@NotNull Duration elapsed) {
        if (elapsed.isNegative() || elapsed.toMinutes() < 1) {
            return "только что";
        }
        return period(elapsed) + " назад";
    }
}
