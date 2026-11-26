package org.pnfolder.pnlibrary.core.menu.style;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.config.ConfigSetting;

/** How numbers of the settings menu are written, stepped and cut into the buttons of the number editor. */
public final class ConfigNumbers {
    private static final int LADDER = 4;
    private static final double TEN = 10.0;
    private static final long SECOND = TimeUnit.SECONDS.toMillis(1);
    private static final long MINUTE = TimeUnit.MINUTES.toMillis(1);
    private static final long HOUR = TimeUnit.HOURS.toMillis(1);
    private static final long DAY = TimeUnit.DAYS.toMillis(1);
    private static final long[] PART_SIZES = {DAY, HOUR, MINUTE, SECOND, 1L};
    private static final String[] PART_WORDS = {"д", "ч", "мин", "с", "мс"};

    /** Sizes of one step for a time kept in each unit, smallest first. */
    private static final Map<TimeUnit, long[]> TIME_STEPS = new EnumMap<>(TimeUnit.class);

    static {
        TIME_STEPS.put(TimeUnit.MILLISECONDS, new long[] {1L, 100L, SECOND, 10 * SECOND});
        TIME_STEPS.put(TimeUnit.SECONDS, new long[] {SECOND, MINUTE, 10 * MINUTE, HOUR});
        TIME_STEPS.put(TimeUnit.MINUTES, new long[] {MINUTE, 10 * MINUTE, HOUR, DAY});
        TIME_STEPS.put(TimeUnit.HOURS, new long[] {HOUR, 6 * HOUR, DAY, 7 * DAY});
        TIME_STEPS.put(TimeUnit.DAYS, new long[] {DAY, 7 * DAY, 30 * DAY, 365 * DAY});
    }

    private ConfigNumbers() {
    }

    /** The value with its unit, and for a time also written out: {@code 5400 сек (1 ч 30 мин)}. */
    public static @NotNull String shown(@NotNull ConfigSetting setting) {
        String value = setting.getValue();
        if (value == null) {
            return "?";
        }
        TimeUnit unit = setting.getDuration();
        if (unit == null) {
            return setting.getUnit().trim().isEmpty() ? value : value + " " + setting.getUnit();
        }
        double number;
        try {
            number = Double.parseDouble(value.trim());
        } catch (NumberFormatException notANumber) {
            return value;
        }
        return value + " " + unitWord(unit) + " (" + human((long) (number * unit.toMillis(1))) + ")";
    }

    /** A length of time in words: {@code 1 ч 30 мин}. */
    public static @NotNull String human(long millis) {
        if (millis <= 0) {
            return "0 с";
        }
        long rest = millis;
        List<String> words = new ArrayList<>();
        for (int index = 0; index < PART_SIZES.length; index++) {
            long count = rest / PART_SIZES[index];
            rest %= PART_SIZES[index];
            if (count > 0) {
                words.add(count + " " + PART_WORDS[index]);
            }
        }
        return String.join(" ", words.subList(0, Math.min(words.size(), LADDER)));
    }

    /** The four step sizes of a number, smallest first: the label and the amount in the unit the field keeps. */
    public static @NotNull List<NumberStep> ladder(@NotNull ConfigSetting setting) {
        TimeUnit unit = setting.getDuration();
        return unit != null ? timeLadder(setting, unit) : plainLadder(setting);
    }

    private static List<NumberStep> timeLadder(ConfigSetting setting, TimeUnit unit) {
        long one = Math.max(unit.toMillis(1), 1);
        long[] sizes = TIME_STEPS.get(unit);
        if (sizes == null) {
            sizes = new long[] {one, one * 10, one * 100, one * 1000};
        }
        List<NumberStep> ladder = new ArrayList<>();
        for (long size : sizes) {
            double amount = (double) size / one;
            if (fits(amount, setting) && ladder.size() < LADDER) {
                ladder.add(new NumberStep(human(size), amount));
            }
        }
        return ladder.size() >= 2 ? ladder : Arrays.asList(new NumberStep("1", 1.0), new NumberStep("10", TEN));
    }

    private static List<NumberStep> plainLadder(ConfigSetting setting) {
        String label = setting.getUnit().trim().isEmpty() ? "" : " " + setting.getUnit();
        List<Double> amounts = new ArrayList<>();
        double amount = setting.getStep();
        for (int index = 0; index < LADDER; index++) {
            if (fits(amount, setting)) {
                amounts.add(amount);
            }
            amount *= TEN;
        }
        if (amounts.size() < 2) {
            amounts = Arrays.asList(setting.getStep(), setting.getStep() * TEN);
        }
        List<NumberStep> ladder = new ArrayList<>();
        for (double step : amounts) {
            ladder.add(new NumberStep(ConfigLook.number(step) + label, step));
        }
        return ladder;
    }

    private static boolean fits(double amount, ConfigSetting setting) {
        Double low = setting.getMin();
        Double high = setting.getMax();
        return low == null || high == null || amount <= high - low || amount == setting.getStep();
    }

    public static @NotNull String unitWord(@NotNull TimeUnit unit) {
        switch (unit) {
            case MILLISECONDS:
                return "мс";
            case SECONDS:
                return "сек";
            case MINUTES:
                return "мин";
            case HOURS:
                return "ч";
            case DAYS:
                return "дн";
            default:
                return unit.name().toLowerCase(Locale.ROOT);
        }
    }
}
