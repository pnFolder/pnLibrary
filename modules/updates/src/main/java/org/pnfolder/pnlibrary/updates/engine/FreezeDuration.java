package org.pnfolder.pnlibrary.updates.engine;

import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses and validates administrator-supplied update-freeze durations: from one minute to five days. */
public final class FreezeDuration {
    /** The longest pause: updates of a component can never be held for more than five days. */
    public static final Duration MAXIMUM = Duration.ofDays(5);

    private static final Duration MINIMUM = Duration.ofMinutes(1);
    private static final Pattern PATTERN = Pattern.compile("^(\\d+)([mhd])$", Pattern.CASE_INSENSITIVE);

    private FreezeDuration() {
    }

    /** Parses a positive {@code m}, {@code h}, or {@code d} duration within supported bounds. */
    @NotNull
    public static Duration parse(@NotNull String value) {
        Matcher match = PATTERN.matcher(value.trim());
        if (!match.matches()) {
            throw new IllegalArgumentException("Freeze duration must use m, h, or d");
        }
        long amount;
        try {
            amount = Long.parseLong(match.group(1));
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Freeze duration is too large");
        }
        return validate(ofUnit(match.group(2).toLowerCase(Locale.ROOT), amount));
    }

    /** Returns {@code duration} after verifying the supported freeze interval. */
    @NotNull
    public static Duration validate(@NotNull Duration duration) {
        if (duration.compareTo(MINIMUM) < 0 || duration.compareTo(MAXIMUM) > 0) {
            throw new IllegalArgumentException("Freeze duration must be between 1 minute and 5 days");
        }
        return duration;
    }

    private static Duration ofUnit(String unit, long amount) {
        switch (unit) {
            case "m":
                return Duration.ofMinutes(amount);
            case "h":
                return Duration.ofHours(amount);
            case "d":
                return Duration.ofDays(amount);
            default:
                throw new IllegalStateException("Unsupported duration unit");
        }
    }
}
