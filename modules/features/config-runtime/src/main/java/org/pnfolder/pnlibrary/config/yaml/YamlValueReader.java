package org.pnfolder.pnlibrary.config.yaml;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.config.ConfigDefaultOnInvalid;
import org.pnfolder.pnlibrary.api.config.ConfigNodeForm;
import org.pnfolder.pnlibrary.api.config.ConfigOptions;
import org.pnfolder.pnlibrary.api.config.ConfigPolymorphic;
import org.pnfolder.pnlibrary.api.config.ConfigSerializer;
import org.pnfolder.pnlibrary.api.config.ConfigTextForm;
import org.pnfolder.pnlibrary.api.config.UnknownTypePolicy;

/** Fills configuration objects from the plain maps, lists and scalars a YAML parser read. */
final class YamlValueReader {
    private final ConfigObjectIntrospector introspector;
    private final ConfigSerializerResolver serializers;
    private final ConfigPolymorphicResolver polymorphic;
    private final ConfigSchemaInspector schema;
    private final ConfigOptions options;
    private final Consumer<String> warning;
    private final SerializationContexts contexts;
    private final List<String> skipped = new ArrayList<String>();

    YamlValueReader(@NotNull ConfigObjectIntrospector introspector, @NotNull ConfigSerializerResolver serializers,
                    @NotNull ConfigPolymorphicResolver polymorphic, @NotNull ConfigSchemaInspector schema,
                    @NotNull ConfigOptions options, @NotNull Consumer<String> warning,
                    @NotNull SerializationContexts contexts) {
        this.introspector = introspector;
        this.serializers = serializers;
        this.polymorphic = polymorphic;
        this.schema = schema;
        this.options = options;
        this.warning = warning;
        this.contexts = contexts;
    }

    /** Forgets the elements skipped by an earlier read. */
    void startRead() {
        skipped.clear();
    }

    /** The elements skipped since {@link #startRead()}. */
    @NotNull
    List<String> skippedSoFar() {
        return new ArrayList<String>(skipped);
    }

    /** Reads {@code value} as {@code type} for a serializer that decodes nested values. */
    Object decodeNested(Object value, Type type, String at) {
        return located(at, () -> convert(value, type, null, at, Collections.<Annotation>emptyList()));
    }

    void populate(@NotNull Object target, @NotNull Class<?> targetType, @NotNull Map<?, ?> values, @NotNull String prefix) {
        Map<String, Object> byKey = new LinkedHashMap<String, Object>();
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            byKey.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        for (Field field : introspector.fields(targetType)) {
            String fieldKey = introspector.key(field);
            if (!byKey.containsKey(fieldKey)) {
                continue;
            }
            Object raw = byKey.get(fieldKey);
            String path = prefix.isEmpty() ? fieldKey : prefix + "." + fieldKey;
            Object current = introspector.read(field, target);
            Object converted;
            try {
                converted = readField(field, raw, current, path);
            } catch (Exception error) {
                converted = invalidValue(field, raw, current, path, error);
            }
            try {
                field.set(target, converted);
            } catch (IllegalAccessException error) {
                throw new IllegalStateException(error);
            }
        }
    }

    private Object readField(Field field, Object raw, Object current, String path) {
        Object resolved = ConfigValueText.enumAlias(field.getType(), raw, path);
        List<Annotation> annotations = Arrays.asList(field.getAnnotations());
        ConfigSerializer<Object> serializer = serializers.forField(field);
        Object read = serializer == null ? null
                : serializer.deserialize(resolved, contexts.create(field.getGenericType(), path, annotations, current));
        return read != null ? read : convert(resolved, field.getGenericType(), current, path, annotations);
    }

    /** Returns {@code current} for a {@code @ConfigDefaultOnInvalid} field; otherwise throws an error that names {@code path} and the value. */
    private Object invalidValue(Field field, Object raw, Object current, String path, Exception error) {
        if (field.isAnnotationPresent(ConfigDefaultOnInvalid.class)) {
            warning.accept("Invalid value at " + path + " (" + (raw == null ? "null" : raw) + "); using default "
                    + (current == null ? "null" : current));
            return current;
        }
        String detail = "";
        if (field.getType().isEnum()) {
            detail = " Allowed values: " + schema.enumDescription(field.getType()) + ". Default: "
                    + (current == null ? "null" : current) + ".";
        }
        String message = error.getMessage() == null ? "" : error.getMessage();
        if (error instanceof LocatedConfigException || message.startsWith(LocatedConfigException.INVALID_VALUE)) {
            throw rethrown(error);
        }
        throw new IllegalArgumentException(LocatedConfigException.INVALID_VALUE + " " + path + ": "
                + ConfigValueText.shown(raw) + "." + detail + ConfigValueText.reasonSuffix(error), error);
    }

    private static RuntimeException rethrown(Exception error) {
        if (error instanceof RuntimeException) {
            return (RuntimeException) error;
        }
        return new IllegalStateException(error);
    }

    @Nullable
    Object convert(@Nullable Object value, @NotNull Type targetType, @Nullable Object current, @NotNull String path,
                   @NotNull List<Annotation> annotations) {
        Class<?> rawType = introspector.rawClass(targetType);
        ConfigSerializer<Object> serializer = serializers.forType(rawType);
        if (serializer != null) {
            return serializer.deserialize(value, contexts.create(targetType, path, Collections.<Annotation>emptyList(), current));
        }
        if (value == null) {
            return null;
        }
        if (polymorphic.supports(targetType, annotations)) {
            return polymorphic(value, rawType, path, annotations);
        }
        Object scalar = ScalarConversion.convert(rawType, value);
        if (scalar != ScalarConversion.NOT_SCALAR) {
            return scalar;
        }
        if (rawType.isArray() && value instanceof List) {
            return convertArray(rawType, (List<?>) value, path);
        }
        if (java.util.Collection.class.isAssignableFrom(rawType) && value instanceof List) {
            return convertCollection(targetType, rawType, (List<?>) value, path, annotations);
        }
        if (Map.class.isAssignableFrom(rawType) && value instanceof Map) {
            return convertMap(targetType, rawType, (Map<?, ?>) value, path);
        }
        if (value instanceof Map) {
            return convertSection(rawType, (Map<?, ?>) value, current, path);
        }
        return value;
    }

    private Object convertArray(Class<?> rawType, List<?> value, String path) {
        Class<?> component = rawType.getComponentType();
        Object array = Array.newInstance(component, value.size());
        for (int index = 0; index < value.size(); index++) {
            Array.set(array, index, convert(value.get(index), component, null, path + "[" + index + "]",
                    Collections.<Annotation>emptyList()));
        }
        return array;
    }

    private Object convertCollection(Type targetType, Class<?> rawType, List<?> value, String path, List<Annotation> annotations) {
        Type elementType = typeArgument(targetType, 0, Object.class);
        boolean skipUnknown = options.getUnknownTypes() == UnknownTypePolicy.SKIP;
        List<Object> converted = new ArrayList<Object>();
        for (int index = 0; index < value.size(); index++) {
            String at = path + "[" + index + "]";
            Object item = value.get(index);
            Object element;
            try {
                element = located(at, () -> convert(item, elementType, null, at, annotations));
            } catch (IllegalArgumentException error) {
                UnknownConfigTypeException unknown = unknownTypeIn(error);
                if (!skipUnknown || unknown == null) {
                    throw error;
                }
                // The element stays in the file; only the copy in memory goes without it.
                String note = at + ": " + unknown.getMessage();
                skipped.add(note);
                warning.accept("Skipped " + note + ". The element stays in the file and is read again when the type appears.");
                continue;
            }
            if (element != null) {
                converted.add(element);
            }
        }
        return introspector.collection(rawType, converted, path);
    }

    private static UnknownConfigTypeException unknownTypeIn(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof UnknownConfigTypeException) {
                return (UnknownConfigTypeException) current;
            }
        }
        return null;
    }

    private Object convertMap(Type targetType, Class<?> rawType, Map<?, ?> value, String path) {
        Type keyType = typeArgument(targetType, 0, String.class);
        Type valueType = typeArgument(targetType, 1, Object.class);
        Map<Object, Object> converted = new LinkedHashMap<Object, Object>();
        for (Map.Entry<?, ?> entry : value.entrySet()) {
            String at = path + "." + entry.getKey();
            Object convertedKey = convert(entry.getKey(), keyType, null, "", Collections.<Annotation>emptyList());
            converted.put(convertedKey, located(at, () -> convert(entry.getValue(), valueType, null, at,
                    Collections.<Annotation>emptyList())));
        }
        return introspector.map(rawType, converted, path);
    }

    private static Type typeArgument(Type type, int index, Type fallback) {
        if (type instanceof ParameterizedType) {
            Type[] arguments = ((ParameterizedType) type).getActualTypeArguments();
            if (index < arguments.length) {
                return arguments[index];
            }
        }
        return fallback;
    }

    private Object convertSection(Class<?> rawType, Map<?, ?> value, Object current, String path) {
        if (rawType.isInterface() || Modifier.isAbstract(rawType.getModifiers())) {
            throw new IllegalArgumentException("Type " + rawType.getName() + " at " + path + " is abstract; register a ConfigSerializer");
        }
        Object nested = current != null ? current : introspector.instantiate(rawType, path);
        populate(nested, rawType, value, path);
        return nested;
    }

    private Object polymorphic(Object value, Class<?> baseType, String path, List<Annotation> annotations) {
        if (value instanceof String) {
            ConfigTextForm form = baseType.getAnnotation(ConfigTextForm.class);
            if (form == null) {
                throw new IllegalArgumentException("Polymorphic value at " + path + " must be a YAML object");
            }
            return serializers.textParser(form.value()).parse((String) value, contexts.create(baseType, path, annotations, null));
        }
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException("Polymorphic value at " + path + " must be a YAML object");
        }
        Map<?, ?> node = rewritten((Map<?, ?>) value, baseType, path);
        String discriminator = baseType.getAnnotation(ConfigPolymorphic.class).discriminator();
        Map.Entry<?, ?> entry = null;
        for (Map.Entry<?, ?> candidate : node.entrySet()) {
            if (candidate.getKey() != null && candidate.getKey().toString().equalsIgnoreCase(discriminator)) {
                entry = candidate;
                break;
            }
        }
        if (entry == null) {
            throw new IllegalStateException("Missing '" + discriminator + "' for " + baseType.getSimpleName() + " at " + path);
        }
        String name = entry.getValue() == null ? "" : YamlText.trim(entry.getValue().toString());
        Class<?> implementation = polymorphic.decodingType(baseType, annotations, name, path);
        Map<Object, Object> body = new LinkedHashMap<Object, Object>();
        for (Map.Entry<?, ?> other : node.entrySet()) {
            if (!java.util.Objects.equals(other.getKey(), entry.getKey())) {
                body.put(other.getKey(), other.getValue());
            }
        }
        Object created = introspector.instantiate(implementation, path);
        populate(created, implementation, body, path);
        return created;
    }

    private Map<?, ?> rewritten(Map<?, ?> value, Class<?> baseType, String path) {
        ConfigNodeForm form = baseType.getAnnotation(ConfigNodeForm.class);
        if (form == null) {
            return value;
        }
        Map<String, Object> node = new LinkedHashMap<String, Object>();
        for (Map.Entry<?, ?> entry : value.entrySet()) {
            node.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return serializers.nodeRewriter(form.value()).rewrite(node, path);
    }

    /** Runs {@code block}; a failure is reported at {@code location} with its own reason, once, at the deepest entry. */
    <R> R located(String location, Supplier<R> block) {
        try {
            return block.get();
        } catch (LocatedConfigException error) {
            throw error;
        } catch (IllegalArgumentException error) {
            String reason = error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();
            if (reason.startsWith(LocatedConfigException.INVALID_VALUE)) {
                throw error;
            }
            throw new LocatedConfigException(location, reason, error);
        }
    }
}
