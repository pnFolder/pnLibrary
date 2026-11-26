package org.pnfolder.pnlibrary.config.yaml.schema;

import org.pnfolder.pnlibrary.config.yaml.ConfigSerializerResolver;
import org.pnfolder.pnlibrary.config.yaml.text.YamlText;
import java.util.Objects;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.config.ConfigAlias;
import org.pnfolder.pnlibrary.api.config.ConfigComment;
import org.pnfolder.pnlibrary.api.config.ConfigNewLine;
import org.pnfolder.pnlibrary.api.config.ConfigNotBlank;
import org.pnfolder.pnlibrary.api.config.ConfigPattern;
import org.pnfolder.pnlibrary.api.config.ConfigProblem;
import org.pnfolder.pnlibrary.api.config.ConfigRange;
import org.pnfolder.pnlibrary.api.config.ConfigRequired;

/**
 * Describes and validates the declarative schema of configuration objects.
 *
 * <p>This class contains no YAML parsing logic. It only interprets field annotations
 * and follows nested configuration objects that are not handled by serializers.
 */
public final class ConfigSchemaInspector {
    private final ConfigObjectIntrospector introspector;
    private final ConfigSerializerResolver serializers;

    public ConfigSchemaInspector(@NotNull ConfigObjectIntrospector introspector, @NotNull ConfigSerializerResolver serializers) {
        this.introspector = Objects.requireNonNull(introspector, "introspector");
        this.serializers = Objects.requireNonNull(serializers, "serializers");
    }

    /** Dotted paths of every {@code @ConfigRequired} field of {@code rootType}, including nested sections. */
    @NotNull
    public Set<String> requiredPaths(@NotNull Class<?> rootType) {
        return requiredPaths(rootType, "");
    }

    /** Builds comment and layout metadata used when rendering generated YAML. */
    @NotNull
    public Map<String, YamlFieldMetadata> metadata(@NotNull Class<?> type, @Nullable Object instance) {
        Map<String, YamlFieldMetadata> result = new LinkedHashMap<>();
        for (Field field : introspector.fields(type)) {
            Object value = instance == null ? null : introspector.read(field, instance);
            ConfigComment comment = field.getAnnotation(ConfigComment.class);
            List<String> comments = new ArrayList<>();
            if (comment != null) {
                comments.addAll(Arrays.asList(comment.value()));
            }
            if (field.getType().isEnum()) {
                comments.add("Allowed values: " + enumDescription(field.getType()) + ". Default: " + (value == null ? "null" : value) + ".");
            }
            Map<String, YamlFieldMetadata> children = isNestedObject(field)
                    ? metadata(field.getType(), value) : Collections.emptyMap();
            result.put(introspector.key(field),
                    new YamlFieldMetadata(comments, field.isAnnotationPresent(ConfigNewLine.class), children));
        }
        return result;
    }

    /** Checks the range, not-blank and pattern annotations of {@code value} and its nested sections. */
    @NotNull
    public List<ConfigProblem> validate(@Nullable Object value, @NotNull Class<?> type) {
        List<ConfigProblem> problems = new ArrayList<>();
        validateObject(value, type, "", problems);
        return problems;
    }

    /** Human-readable enum constants and aliases for error messages. */
    @NotNull
    public String enumDescription(@NotNull Class<?> type) {
        StringBuilder text = new StringBuilder();
        for (Object raw : type.getEnumConstants()) {
            Enum<?> constant = (Enum<?>) raw;
            if (text.length() > 0) {
                text.append(", ");
            }
            ConfigAlias alias = enumField(type, constant).getAnnotation(ConfigAlias.class);
            String[] aliases = alias == null ? new String[0] : alias.value();
            if (aliases.length == 0) {
                text.append(constant.name());
            } else {
                text.append(constant.name()).append(" (aliases: ").append(String.join(", ", aliases)).append(")");
            }
        }
        return text.toString();
    }

    private static Field enumField(Class<?> type, Enum<?> constant) {
        try {
            return type.getField(constant.name());
        } catch (NoSuchFieldException error) {
            throw new IllegalStateException(error);
        }
    }

    private Set<String> requiredPaths(Class<?> type, String prefix) {
        if (serializers.forType(type) != null) {
            return Collections.emptySet();
        }
        Set<String> paths = new LinkedHashSet<>();
        for (Field field : introspector.fields(type)) {
            String path = nestedPath(prefix, introspector.key(field));
            if (field.isAnnotationPresent(ConfigRequired.class)) {
                paths.add(path);
            }
            if (isNestedObject(field)) {
                paths.addAll(requiredPaths(field.getType(), path));
            }
        }
        return paths;
    }

    private void validateObject(Object value, Class<?> type, String prefix, List<ConfigProblem> problems) {
        if (value == null) {
            return;
        }
        for (Field field : introspector.fields(type)) {
            Object current = introspector.read(field, value);
            String path = nestedPath(prefix, introspector.key(field));
            ConfigRange range = field.getAnnotation(ConfigRange.class);
            if (range != null) {
                double number = current instanceof Number ? ((Number) current).doubleValue() : Double.NaN;
                if (!(current instanceof Number) || !(number >= range.min() && number <= range.max())) {
                    problems.add(new ConfigProblem(path, "must be between " + range.min() + " and " + range.max()));
                }
            }
            if (field.isAnnotationPresent(ConfigNotBlank.class)
                    && (!(current instanceof String) || YamlText.isBlank((String) current))) {
                problems.add(new ConfigProblem(path, "must not be blank"));
            }
            ConfigPattern pattern = field.getAnnotation(ConfigPattern.class);
            if (pattern != null && (!(current instanceof String) || !Pattern.matches(pattern.value(), (String) current))) {
                problems.add(new ConfigProblem(path, "must match " + pattern.value()));
            }
            if (current != null && isNestedObject(field)) {
                validateObject(current, field.getType(), path, problems);
            }
        }
    }

    private boolean isNestedObject(Field field) {
        return serializers.forField(field) == null
                && serializers.forType(field.getType()) == null
                && !introspector.isScalar(field.getType())
                && !Collection.class.isAssignableFrom(field.getType())
                && !Map.class.isAssignableFrom(field.getType())
                && !field.getType().isArray();
    }

    private static String nestedPath(String prefix, String key) {
        return prefix.isEmpty() ? key : prefix + "." + key;
    }
}
