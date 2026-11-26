package org.pnfolder.pnlibrary.config.yaml.text;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.config.ConfigAlias;

/** Text pieces of configuration error messages and enum alias handling. */
public final class ConfigValueText {
    private static final int MAX_SHOWN = 120;

    private ConfigValueText() {
    }

    /** A value for an error message, cut to a readable length. */
    @NotNull
    public static String shown(@Nullable Object raw) {
        String text = raw == null ? "null" : raw.toString();
        return text.length() > MAX_SHOWN ? text.substring(0, MAX_SHOWN) + "..." : text;
    }

    /** The " Reason: ..." tail of an error message, or an empty text when {@code error} has no useful reason. */
    @NotNull
    public static String reasonSuffix(@NotNull Exception error) {
        if (error instanceof IllegalArgumentException && error.getMessage() != null && !YamlText.isBlank(error.getMessage())) {
            return " Reason: " + error.getMessage();
        }
        return "";
    }

    /** Replaces a legacy {@code @ConfigAlias} spelling in {@code value} with the enum constant name; other values pass unchanged. */
    @Nullable
    public static Object enumAlias(@NotNull Class<?> type, @Nullable Object value, @NotNull String path) {
        if (!type.isEnum() || !(value instanceof String)) {
            return value;
        }
        String found = null;
        int matches = 0;
        for (Object raw : type.getEnumConstants()) {
            String name = ((Enum<?>) raw).name();
            ConfigAlias alias = field(type, name).getAnnotation(ConfigAlias.class);
            if (alias == null) {
                continue;
            }
            for (String spelling : alias.value()) {
                if (spelling.equalsIgnoreCase((String) value)) {
                    matches++;
                    found = name;
                    break;
                }
            }
        }
        if (matches > 1) {
            throw new IllegalArgumentException("Enum alias '" + value + "' is ambiguous at " + path + " in " + type.getName());
        }
        return found != null ? found : value;
    }

    private static java.lang.reflect.Field field(Class<?> type, String name) {
        try {
            return type.getField(name);
        } catch (NoSuchFieldException error) {
            throw new IllegalStateException(error);
        }
    }
}
