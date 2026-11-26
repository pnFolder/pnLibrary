package org.pnfolder.pnlibrary.config.yaml;

import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Reads a length of time typed as {@code 1h30m}, {@code 90 мин} or {@code 45s} into the unit a setting keeps. */
final class DurationText {
    private static final Pattern PART = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(ms|мс|сек|с|s|мин|м|m|ч|h|д|d)");

    private DurationText() {
    }

    /** The time in {@code unit}, or {@code null} when {@code text} has no unit letters (a plain number) or {@code unit} is {@code null}. */
    @Nullable
    static Long parse(@NotNull String text, @Nullable TimeUnit unit) {
        if (unit == null) {
            return null;
        }
        String cleaned = YamlText.trim(text).toLowerCase(Locale.ROOT);
        Matcher matcher = PART.matcher(cleaned);
        StringBuilder joined = new StringBuilder();
        long millis = 0;
        boolean found = false;
        while (matcher.find()) {
            found = true;
            joined.append(matcher.group());
            double amount = Double.parseDouble(matcher.group(1).replace(',', '.'));
            millis += (long) (amount * millisOf(matcher.group(2)));
        }
        if (!found || !joined.toString().replace(" ", "").equals(cleaned.replace(" ", ""))) {
            return null;
        }
        return unit.convert(millis, TimeUnit.MILLISECONDS);
    }

    private static long millisOf(String letters) {
        switch (letters) {
            case "ms":
            case "мс":
                return 1L;
            case "с":
            case "s":
            case "сек":
                return 1_000L;
            case "м":
            case "m":
            case "мин":
                return 60_000L;
            case "ч":
            case "h":
                return 3_600_000L;
            default:
                return 86_400_000L;
        }
    }
}
