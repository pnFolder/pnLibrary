package org.pnfolder.pnlibrary.api.placeholders.argument;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.placeholders.info.PlayerRef;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The converters of argument types for the whole server: the built-in ones and the types of the library. A module
 * should use {@code placeholders.converter(...)} instead: that one belongs to the module and goes away with it.
 */
public final class ArgumentAdapters {
    private static final ConcurrentHashMap<Class<?>, ArgumentConverter<?>> ADAPTERS = new ConcurrentHashMap<>();
    private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_.-]{1,36}");
    private static final Pattern DURATION = Pattern.compile("(?:(\\d+)d)?(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s)?", Pattern.CASE_INSENSITIVE);

    static {
        register(Integer.class, new ArgumentAdapter<Integer>("int", text -> {
            try {
                return Integer.valueOf(text);
            } catch (NumberFormatException notInt) {
                return null;
            }
        }));
        register(Long.class, new ArgumentAdapter<Long>("long", text -> {
            try {
                return Long.valueOf(text);
            } catch (NumberFormatException notLong) {
                return null;
            }
        }));
        register(Double.class, new ArgumentAdapter<Double>("number", ArgumentAdapters::parseFinite));
        register(String.class, new ArgumentAdapter<String>("text", text -> text));
        register(Boolean.class, new ArgumentAdapter<Boolean>("yes/no", ArgumentAdapters::parseBoolean));
        register(UUID.class, new ArgumentAdapter<UUID>("uuid", text -> {
            try {
                return UUID.fromString(text);
            } catch (IllegalArgumentException notUuid) {
                return null;
            }
        }));
        register(Duration.class, new ArgumentAdapter<Duration>("duration, like 90s, 15m, 1h30m or 2d", ArgumentAdapters::parseDuration));
        register(PlayerRef.class, new ArgumentAdapter<PlayerRef>("player", Collections.<String>emptyList(),
                viewer -> viewer.getPlayerId() == null ? null : new PlayerRef(viewer.getPlayerId().toString()),
                text -> PLAYER_NAME.matcher(text).matches() ? new PlayerRef(text) : null));
    }

    private ArgumentAdapters() {
    }

    /** Adds or replaces the converter of {@code type}: the one used for it when the parameter names none. */
    public static <T> void register(@NotNull Class<T> type, @NotNull ArgumentConverter<T> converter) {
        ADAPTERS.put(type, converter);
    }

    /** Adds or replaces the converter of {@code type} made of a {@code label} and a {@code parse} function. */
    public static <T> void register(@NotNull Class<T> type, @NotNull String label, @NotNull Function<String, T> parse) {
        register(type, new ArgumentAdapter<T>(label, parse));
    }

    /** Removes the adapter of {@code type}. Built-in types come back as they were only after a restart. */
    public static void unregister(@NotNull Class<?> type) {
        ADAPTERS.remove(type);
    }

    /** The converter of {@code type}, enums included, or {@code null} when the type is unknown. */
    @SuppressWarnings("unchecked")
    @Nullable
    public static <T> ArgumentConverter<T> find(@NotNull Class<T> type) {
        ArgumentConverter<?> adapter = ADAPTERS.get(type);
        if (adapter != null) {
            return (ArgumentConverter<T>) adapter;
        }
        return type.isEnum() ? enumAdapter(type) : null;
    }

    /** The types that have an adapter, for the administrator's overview. */
    @NotNull
    public static List<String> types() {
        List<String> names = new ArrayList<>();
        for (Class<?> type : ADAPTERS.keySet()) {
            names.add(type.getSimpleName());
        }
        Collections.sort(names);
        return names;
    }

    private static <T> ArgumentAdapter<T> enumAdapter(Class<T> type) {
        T[] constants = type.getEnumConstants();
        List<String> names = new ArrayList<>();
        for (T constant : constants) {
            names.add(((Enum<?>) constant).name().toLowerCase(Locale.ROOT));
        }
        return new ArgumentAdapter<T>("one of the words", names, text -> {
            for (T constant : constants) {
                if (((Enum<?>) constant).name().equalsIgnoreCase(text)) {
                    return constant;
                }
            }
            return null;
        });
    }

    @Nullable
    private static Double parseFinite(String text) {
        try {
            double number = Double.parseDouble(text);
            return Double.isInfinite(number) || Double.isNaN(number) ? null : Double.valueOf(number);
        } catch (NumberFormatException notNumber) {
            return null;
        }
    }

    @Nullable
    private static Boolean parseBoolean(String text) {
        switch (text.toLowerCase(Locale.ROOT)) {
            case "true": case "yes": case "1": case "on": case "да": case "вкл":
                return Boolean.TRUE;
            case "false": case "no": case "0": case "off": case "нет": case "выкл":
                return Boolean.FALSE;
            default:
                return null;
        }
    }

    @Nullable
    private static Duration parseDuration(String text) {
        Matcher match = DURATION.matcher(text);
        if (!match.matches()) {
            return null;
        }
        long[] parts = new long[4];
        boolean any = false;
        for (int i = 0; i < 4; i++) {
            String group = match.group(i + 1);
            if (group != null && !group.isEmpty()) {
                any = true;
                try {
                    parts[i] = Long.parseLong(group);
                } catch (NumberFormatException tooLarge) {
                    parts[i] = 0L;
                }
            }
        }
        if (!any) {
            return null;
        }
        return Duration.ofDays(parts[0]).plusHours(parts[1]).plusMinutes(parts[2]).plusSeconds(parts[3]);
    }
}
