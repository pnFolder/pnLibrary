package org.pnfolder.pnlibrary.core.placeholders.format;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.placeholders.info.ViewerInfo;

/** The formats every placeholder can use. Numbers and dates follow the language of the viewer. */
public final class BuiltInFormats {
    private BuiltInFormats() {
    }

    /** Adds every built-in format to {@code registry}. */
    public static void register(@NotNull FormatRegistry registry) {
        registerNumbers(registry);
        registerText(registry);
        registerOthers(registry);
    }

    private static void add(FormatRegistry registry, String name, Class<?> input, FormatApply apply) {
        registry.add(new FormatDef("", name, input, apply));
    }

    private static void registerNumbers(FormatRegistry registry) {
        add(registry, "group", Number.class, (n, a, v) -> NumberText.group((Number) n, v));
        add(registry, "short", Number.class, (n, a, v) -> NumberText.shorten((Number) n, v));
        add(registry, "round", Number.class, (n, a, v) -> NumberText.round((Number) n, intArgument(a, 0), v));
        add(registry, "percent", Number.class, (n, a, v) -> NumberText.round(((Number) n).doubleValue() * 100, 1, v) + "%");
        add(registry, "pad", Number.class, (n, a, v) -> pad(((Number) n).longValue(), intArgument(a, 2)));
        add(registry, "sign", Number.class, (n, a, v) -> ((Number) n).doubleValue() > 0
                ? "+" + ((Number) n).longValue() : String.valueOf(((Number) n).longValue()));
        add(registry, "roman", Number.class, (n, a, v) -> NumberText.roman(((Number) n).intValue()));
        add(registry, "ifpos", Number.class, (n, a, v) -> ((Number) n).doubleValue() > 0 ? word(a, 0, "") : word(a, 1, ""));
    }

    private static void registerText(FormatRegistry registry) {
        add(registry, "upper", Object.class, (t, a, v) -> t.toString().toUpperCase(Locale.ROOT));
        add(registry, "lower", Object.class, (t, a, v) -> t.toString().toLowerCase(Locale.ROOT));
        add(registry, "title", Object.class, (t, a, v) -> title(t.toString()));
        add(registry, "cut", Object.class, (t, a, v) -> cut(t.toString(), intArgument(a, 16)));
        add(registry, "wrap", Object.class, (t, a, v) -> word(a, 0, "[") + t + word(a, 1, "]"));
        add(registry, "default", Object.class, (t, a, v) -> t);
        add(registry, "map", Object.class, (value, a, v) -> mapped(value, a));
    }

    private static void registerOthers(FormatRegistry registry) {
        add(registry, "yesno", Boolean.class, (b, a, v) -> yesNo((Boolean) b, v));
        add(registry, "icon", Boolean.class, (b, a, v) -> (Boolean) b ? "✔" : "✘");
        add(registry, "left", Duration.class, (d, a, v) -> TimeText.left((Duration) d, v));
        add(registry, "left", Number.class, (n, a, v) -> TimeText.left(Duration.ofSeconds(((Number) n).longValue()), v));
        add(registry, "hms", Duration.class, (d, a, v) -> TimeText.hms((Duration) d));
        add(registry, "hms", Number.class, (n, a, v) -> TimeText.hms(Duration.ofSeconds(((Number) n).longValue())));
    }

    private static int intArgument(List<String> arguments, int fallback) {
        if (arguments.isEmpty()) {
            return fallback;
        }
        try {
            return Integer.parseInt(arguments.get(0));
        } catch (NumberFormatException failure) {
            return fallback;
        }
    }

    private static String word(List<String> arguments, int index, String fallback) {
        return index < arguments.size() ? arguments.get(index) : fallback;
    }

    private static String pad(long number, int width) {
        StringBuilder text = new StringBuilder(String.valueOf(number));
        while (text.length() < width) {
            text.insert(0, '0');
        }
        return text.toString();
    }

    private static String title(String text) {
        StringBuilder result = new StringBuilder();
        String[] words = text.split(" ", -1);
        for (int i = 0; i < words.length; i++) {
            if (i > 0) {
                result.append(' ');
            }
            String part = words[i];
            result.append(part.isEmpty() ? part : part.substring(0, 1).toUpperCase(Locale.ROOT) + part.substring(1));
        }
        return result.toString();
    }

    private static String cut(String text, int limit) {
        if (text.length() <= limit) {
            return text;
        }
        return text.substring(0, limit) + "…";
    }

    private static Object mapped(Object value, List<String> arguments) {
        for (String pair : arguments) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2 && parts[0].equals(value.toString())) {
                return parts[1];
            }
        }
        return value;
    }

    private static String yesNo(boolean value, ViewerInfo viewer) {
        if (NumberText.isRussian(viewer)) {
            return value ? "да" : "нет";
        }
        return value ? "yes" : "no";
    }
}
