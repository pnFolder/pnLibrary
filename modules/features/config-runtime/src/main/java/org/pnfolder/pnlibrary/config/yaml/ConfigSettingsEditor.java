package org.pnfolder.pnlibrary.config.yaml;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.config.ConfigDuration;
import org.pnfolder.pnlibrary.api.config.ConfigEditException;
import org.pnfolder.pnlibrary.api.config.ConfigEditable;
import org.pnfolder.pnlibrary.api.config.ConfigHint;
import org.pnfolder.pnlibrary.api.config.ConfigRange;
import org.pnfolder.pnlibrary.api.config.ConfigSecret;
import org.pnfolder.pnlibrary.api.config.ConfigSetting;
import org.pnfolder.pnlibrary.api.config.ConfigSettingKind;
import org.pnfolder.pnlibrary.api.config.ConfigStep;

/**
 * Finds the fields marked {@code @ConfigEditable} in a configuration class and changes them from typed text.
 *
 * <p>Fields are found by looking through nested sections; a {@code @ConfigSecret} field and everything under it is never offered.
 * Changing a value sets the field of the loaded object; the caller validates and writes the file and calls
 * {@link Applied#restore()} when that fails.
 */
public final class ConfigSettingsEditor {
    private static final int MAX_DEPTH = 8;
    private static final Set<Class<?>> WHOLE = new HashSet<Class<?>>(Arrays.<Class<?>>asList(
            int.class, Integer.class, long.class, Long.class, short.class, Short.class, byte.class, Byte.class));
    private static final Set<Class<?>> DECIMAL = new HashSet<Class<?>>(Arrays.<Class<?>>asList(
            double.class, Double.class, float.class, Float.class));

    private final ConfigObjectIntrospector introspector;
    private final Map<String, EditableEntry> entries = new LinkedHashMap<String, EditableEntry>();

    /** What {@link #set} did: the setting with its new value, the old text, and a way to put the old value back. */
    public static final class Applied {
        private final ConfigSetting setting;
        private final String previous;
        private final Runnable restore;

        Applied(ConfigSetting setting, String previous, Runnable restore) {
            this.setting = setting;
            this.previous = previous;
            this.restore = restore;
        }

        /** The setting with its new value. */
        @NotNull
        public ConfigSetting getSetting() {
            return setting;
        }

        /** The text of the old value, or {@code null} when it was empty. */
        @Nullable
        public String getPrevious() {
            return previous;
        }

        /** Puts the old value back. */
        public void restore() {
            restore.run();
        }
    }

    /** Collects the editable fields of {@code rootType}. */
    public ConfigSettingsEditor(@NotNull ConfigObjectIntrospector introspector, @NotNull Class<?> rootType) {
        this.introspector = introspector;
        List<EditableEntry> found = new ArrayList<EditableEntry>();
        collect(rootType, "", Collections.<Field>emptyList(), 0, found);
        for (EditableEntry entry : found) {
            entries.put(entry.path, entry);
        }
    }

    /** Whether the class has no editable field. */
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /** The settings with their current values taken from {@code root}, or without values when it is {@code null}. */
    @NotNull
    public List<ConfigSetting> describe(@Nullable Object root) {
        List<ConfigSetting> settings = new ArrayList<ConfigSetting>();
        for (EditableEntry entry : entries.values()) {
            settings.add(setting(entry, root));
        }
        return settings;
    }

    /** Sets the field behind {@code path} from {@code text}; nothing is written to disk here. */
    @NotNull
    public Applied set(@NotNull Object root, @NotNull String path, @NotNull String text) {
        EditableEntry entry = entries.get(path);
        if (entry == null) {
            throw new ConfigEditException("Такой настройки нет: " + path + "."
                    + (entries.isEmpty() ? "" : " Доступны: " + String.join(", ", entries.keySet())));
        }
        Object target = resolve(root, entry);
        Object old = read(entry.field(), target);
        Object parsed = SettingValueParser.parse(entry, text);
        write(entry.field(), target, parsed);
        ConfigSetting updated = setting(entry, root);
        return new Applied(updated, display(old), () -> write(entry.field(), target, old));
    }

    private static Object read(Field field, Object owner) {
        try {
            return field.get(owner);
        } catch (IllegalAccessException error) {
            throw new IllegalStateException(error);
        }
    }

    private static void write(Field field, Object owner, Object value) {
        try {
            field.set(owner, value);
        } catch (IllegalAccessException error) {
            throw new IllegalStateException(error);
        }
    }

    private Object resolve(Object root, EditableEntry entry) {
        Object current = root;
        for (Field field : entry.chain.subList(0, entry.chain.size() - 1)) {
            current = read(field, current);
            if (current == null) {
                throw new ConfigEditException("Раздел " + field.getName() + " пуст, значение не изменить");
            }
        }
        return current;
    }

    private ConfigSetting setting(EditableEntry entry, Object root) {
        ConfigRange range = entry.field().getAnnotation(ConfigRange.class);
        String value = null;
        if (root != null) {
            try {
                value = display(read(entry.field(), resolve(root, entry)));
            } catch (RuntimeException error) {
                value = null;
            }
        }
        String title = YamlText.isBlank(entry.annotation.title()) ? introspector.key(entry.field()) : entry.annotation.title();
        ConfigStep step = entry.field().getAnnotation(ConfigStep.class);
        ConfigDuration duration = entry.field().getAnnotation(ConfigDuration.class);
        ConfigHint hint = hint(entry);
        return ConfigSetting.builder(entry.path, title, entry.annotation.description(), entry.kind, value)
                .choices(SettingValueParser.choicesOf(entry))
                .min(range != null && range.min() != -Double.MAX_VALUE ? Double.valueOf(range.min()) : null)
                .max(range != null && range.max() != Double.MAX_VALUE ? Double.valueOf(range.max()) : null)
                .apply(entry.annotation.apply())
                .unit(step == null ? "" : step.unit())
                .step(step == null ? 1.0 : step.step())
                .duration(duration == null ? null : duration.unit())
                .format(hint == null ? "" : hint.format())
                .examples(hint == null ? Collections.<String>emptyList() : Arrays.asList(hint.examples()))
                .build();
    }

    private static ConfigHint hint(EditableEntry entry) {
        return entry.field().getAnnotation(ConfigHint.class);
    }

    private static String display(Object value) {
        if (value == null) {
            return null;
        }
        return value instanceof Enum ? ((Enum<?>) value).name() : value.toString();
    }

    private void collect(Class<?> type, String prefix, List<Field> chain, int depth, List<EditableEntry> out) {
        if (depth >= MAX_DEPTH) {
            return;
        }
        for (Field field : introspector.fields(type)) {
            if (field.isAnnotationPresent(ConfigSecret.class)) {
                continue;
            }
            String path = prefix + introspector.key(field);
            ConfigEditable annotation = field.getAnnotation(ConfigEditable.class);
            ConfigSettingKind kind = kindOf(field);
            List<Field> extended = new ArrayList<Field>(chain);
            extended.add(field);
            if (annotation != null && kind != null && !Modifier.isFinal(field.getModifiers())) {
                out.add(new EditableEntry(path, extended, annotation, kind));
            } else if (introspector.isSection(field.getType())) {
                collect(field.getType(), path + ".", extended, depth + 1, out);
            }
        }
    }

    private static ConfigSettingKind kindOf(Field field) {
        ConfigHint hint = field.getAnnotation(ConfigHint.class);
        if (field.getType() == String.class && hint != null && hint.strict() && hint.values().length > 0) {
            return ConfigSettingKind.CHOICE;
        }
        Class<?> type = field.getType();
        if (type == boolean.class || type == Boolean.class) {
            return ConfigSettingKind.BOOLEAN;
        }
        if (WHOLE.contains(type)) {
            return ConfigSettingKind.INTEGER;
        }
        if (DECIMAL.contains(type)) {
            return ConfigSettingKind.DECIMAL;
        }
        if (type == String.class) {
            return ConfigSettingKind.TEXT;
        }
        return type.isEnum() ? ConfigSettingKind.CHOICE : null;
    }
}
