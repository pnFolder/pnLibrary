package org.pnfolder.pnlibrary.core.settings;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;

/** Reading and writing the values of settings as text. */
public final class SettingTexts {
    private static final Pattern PART = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(ms|мс|сек|с|s|мин|м|m|ч|h|д|d)");
    private static final Set<String> ON = new HashSet<>(
        Arrays.asList("true", "on", "yes", "да", "вкл", "включено", "1"));
    private static final Set<String> OFF = new HashSet<>(
        Arrays.asList("false", "off", "no", "нет", "выкл", "выключено", "0"));
    private static final long DAY = 86_400_000L;
    private static final long HOUR = 3_600_000L;
    private static final long MINUTE = 60_000L;
    private static final long SECOND = 1_000L;
    private static final long[] SIZES = {DAY, HOUR, MINUTE, SECOND, 1L};
    private static final String[] NAMES = {"д", "ч", "м", "с", "мс"};

    private SettingTexts() {
    }

    /** {@code true} or {@code false} from the usual words for them. */
    public static boolean toSwitch(@NotNull String text) {
        String word = text.trim().toLowerCase(Locale.ROOT);
        if (ON.contains(word)) {
            return true;
        }
        if (OFF.contains(word)) {
            return false;
        }
        throw new SettingException(
            "Нужно значение вкл или выкл (можно также включено, выключено, true, false, on, off, да, нет).");
    }

    /** How a switch is read by people: {@code включено} or {@code выключено}; the stored value stays true or false. */
    public static @NotNull String switchWord(boolean on) {
        return on ? "включено" : "выключено";
    }

    /** A whole number inside {@code min}..{@code max}. */
    public static int number(@NotNull String text, long min, long max) {
        long value;
        try {
            value = Long.parseLong(text.trim());
        } catch (NumberFormatException error) {
            throw new SettingException("Нужно целое число от " + min + " до " + max + ".");
        }
        if (value < min || value > max) {
            throw new SettingException("Число " + value + " вне допустимых границ: от " + min + " до " + max + ".");
        }
        return (int) value;
    }

    /** A length of time such as {@code 6h}, {@code 1h30m}, {@code 90 мин} or {@code 45s}, inside {@code min}..{@code max} milliseconds. */
    public static @NotNull Duration duration(@NotNull String text, long min, long max) {
        String cleaned = text.trim().toLowerCase(Locale.ROOT);
        Matcher matcher = PART.matcher(cleaned);
        StringBuilder matched = new StringBuilder();
        long millis = 0;
        while (matcher.find()) {
            matched.append(matcher.group());
            double amount = Double.parseDouble(matcher.group(1).replace(',', '.'));
            millis += (long) (amount * millisOf(matcher.group(2)));
        }
        if (matched.length() == 0 || !matched.toString().replace(" ", "").equals(cleaned.replace(" ", ""))) {
            throw new SettingException(
                "Нужно время с единицей, например 30s, 15m, 6h, 1h30m (с, м, ч, д тоже годятся).");
        }
        if (millis < min || millis > max) {
            throw new SettingException("Время " + duration(millis) + " вне допустимых границ: от "
                + duration(min) + " до " + duration(max) + ".");
        }
        return Duration.ofMillis(millis);
    }

    /** A length of time as {@code 1д2ч30м15с}, the shortest form that reads back to the same time. */
    public static @NotNull String duration(@NotNull Duration value) {
        return duration(value.toMillis());
    }

    /** A length of {@code millis} milliseconds as {@code 1д2ч30м15с}. */
    public static @NotNull String duration(long millis) {
        if (millis <= 0) {
            return "0с";
        }
        long rest = millis;
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < SIZES.length; index++) {
            long count = rest / SIZES[index];
            rest %= SIZES[index];
            if (count > 0) {
                text.append(count).append(NAMES[index]);
            }
        }
        return text.toString();
    }

    private static long millisOf(String letters) {
        switch (letters) {
            case "ms":
            case "мс":
                return 1L;
            case "с":
            case "s":
            case "сек":
                return SECOND;
            case "м":
            case "m":
            case "мин":
                return MINUTE;
            case "ч":
            case "h":
                return HOUR;
            default:
                return DAY;
        }
    }
}
