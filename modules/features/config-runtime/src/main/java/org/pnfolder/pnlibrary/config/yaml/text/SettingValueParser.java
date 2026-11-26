package org.pnfolder.pnlibrary.config.yaml.text;

import org.pnfolder.pnlibrary.config.yaml.EditableEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.pnfolder.pnlibrary.api.config.ConfigDuration;
import org.pnfolder.pnlibrary.api.config.ConfigEditException;
import org.pnfolder.pnlibrary.api.config.ConfigHint;
import org.pnfolder.pnlibrary.api.config.ConfigSettingKind;

/** Reads the text an administrator typed into the value of an editable field. */
public final class SettingValueParser {
    private SettingValueParser() {
    }

    private static ConfigHint hint(EditableEntry entry) {
        return entry.field().getAnnotation(ConfigHint.class);
    }

    public static List<String> choicesOf(EditableEntry entry) {
        Class<?> type = entry.field().getType();
        List<String> choices = new ArrayList<>();
        if (type.isEnum()) {
            for (Object constant : type.getEnumConstants()) {
                choices.add(((Enum<?>) constant).name());
            }
            return choices;
        }
        ConfigHint hint = hint(entry);
        return hint == null ? choices : Arrays.asList(hint.values());
    }

    public static Object parse(EditableEntry entry, String text) {
        String raw = YamlText.trim(text);
        try {
            switch (entry.kind) {
                case BOOLEAN:
                    return parseBoolean(raw);
                case INTEGER:
                    return parseWhole(entry, raw);
                case DECIMAL:
                    return parseDecimal(entry, raw);
                case TEXT:
                    return text;
                default:
                    return parseChoice(entry, raw);
            }
        } catch (ConfigEditException error) {
            throw error;
        } catch (RuntimeException error) {
            throw new ConfigEditException("«" + raw + "» не подходит для настройки " + entry.path + ": " + hint(entry.kind), error);
        }
    }

    private static Object parseBoolean(String raw) {
        switch (raw.toLowerCase(Locale.ROOT)) {
            case "true":
            case "on":
            case "yes":
            case "1":
            case "вкл":
            case "да":
                return Boolean.TRUE;
            case "false":
            case "off":
            case "no":
            case "0":
            case "выкл":
            case "нет":
                return Boolean.FALSE;
            default:
                throw new ConfigEditException("«" + raw + "» не подходит: нужно true или false");
        }
    }

    private static Object parseWhole(EditableEntry entry, String raw) {
        Class<?> type = entry.field().getType();
        ConfigDuration duration = entry.field().getAnnotation(ConfigDuration.class);
        TimeUnit unit = duration == null ? null : duration.unit();
        Long parsed = DurationText.parse(raw, unit);
        long number = parsed != null ? parsed : Long.parseLong(raw);
        if (type == int.class || type == Integer.class) {
            return Math.toIntExact(number);
        }
        if (type == short.class || type == Short.class) {
            if (number < Short.MIN_VALUE || number > Short.MAX_VALUE) {
                throw new IllegalArgumentException("Failed requirement.");
            }
            return (short) number;
        }
        if (type == byte.class || type == Byte.class) {
            if (number < Byte.MIN_VALUE || number > Byte.MAX_VALUE) {
                throw new IllegalArgumentException("Failed requirement.");
            }
            return (byte) number;
        }
        return number;
    }

    private static Object parseDecimal(EditableEntry entry, String raw) {
        Class<?> type = entry.field().getType();
        double number = Double.parseDouble(raw.replace(',', '.'));
        if (Double.isNaN(number) || Double.isInfinite(number)) {
            throw new IllegalArgumentException("Failed requirement.");
        }
        return type == float.class || type == Float.class ? (Object) Float.valueOf((float) number) : (Object) Double.valueOf(number);
    }

    private static Object parseChoice(EditableEntry entry, String raw) {
        Class<?> type = entry.field().getType();
        if (type.isEnum()) {
            for (Object constant : type.getEnumConstants()) {
                if (((Enum<?>) constant).name().equalsIgnoreCase(raw)) {
                    return constant;
                }
            }
        } else {
            for (String choice : choicesOf(entry)) {
                if (choice.equalsIgnoreCase(raw)) {
                    return choice;
                }
            }
        }
        throw new ConfigEditException("«" + raw + "» не подходит: нужно одно из " + String.join(", ", choicesOf(entry)));
    }

    private static String hint(ConfigSettingKind kind) {
        switch (kind) {
            case INTEGER:
                return "нужно целое число";
            case DECIMAL:
                return "нужно число";
            default:
                return "неверное значение";
        }
    }
}
