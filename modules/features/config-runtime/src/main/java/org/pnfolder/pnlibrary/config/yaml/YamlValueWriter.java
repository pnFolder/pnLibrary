package org.pnfolder.pnlibrary.config.yaml;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.config.ConfigPolymorphic;
import org.pnfolder.pnlibrary.api.config.ConfigSerializer;

/** Turns configuration objects into the plain maps, lists and scalars a YAML dumper writes. */
final class YamlValueWriter {
    private final ConfigObjectIntrospector introspector;
    private final ConfigSerializerResolver serializers;
    private final ConfigPolymorphicResolver polymorphic;
    private final SerializationContexts contexts;

    YamlValueWriter(@NotNull ConfigObjectIntrospector introspector, @NotNull ConfigSerializerResolver serializers,
                    @NotNull ConfigPolymorphicResolver polymorphic, @NotNull SerializationContexts contexts) {
        this.introspector = introspector;
        this.serializers = serializers;
        this.polymorphic = polymorphic;
        this.contexts = contexts;
    }

    /** {@code value} typed by its own class, at the root. */
    @Nullable
    Object write(@Nullable Object value) {
        return write(value, typeOf(value), "", Collections.<Annotation>emptyList(), value);
    }

    /** {@code value} typed as {@code declaredType}, as nested values of serializers are written. */
    @Nullable
    Object write(@Nullable Object value, @NotNull Type declaredType, @NotNull String path) {
        return write(value, declaredType, path, Collections.<Annotation>emptyList(), value);
    }

    @Nullable
    Object write(@Nullable Object value, @NotNull Type declaredType, @NotNull String path,
                 @NotNull List<Annotation> annotations, @Nullable Object defaultValue) {
        if (value != null) {
            ConfigSerializer<Object> serializer = serializers.forType(value.getClass());
            if (serializer != null) {
                return write(serializer.serialize(value, contexts.create(declaredType, path, annotations, defaultValue)));
            }
            if (polymorphic.supports(declaredType, annotations)) {
                return writePolymorphic(value, declaredType, path, annotations);
            }
        }
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Enum) {
            return ((Enum<?>) value).name();
        }
        if (value instanceof Iterable) {
            return writeList((Iterable<?>) value, declaredType, path, annotations);
        }
        if (value instanceof Object[]) {
            return writeArray((Object[]) value, path);
        }
        if (value instanceof Map) {
            return writeMap((Map<?, ?>) value, path);
        }
        return writeObject(value, path);
    }

    private Object writePolymorphic(Object value, Type declaredType, String path, List<Annotation> annotations) {
        Class<?> base = introspector.rawClass(declaredType);
        ConfigPolymorphic marker = base.getAnnotation(ConfigPolymorphic.class);
        ResolvedConfigType selected = polymorphic.encodingType(base, annotations, value.getClass());
        Map<String, Object> written = new LinkedHashMap<String, Object>();
        written.put(marker.discriminator(), selected.serializedName());
        written.putAll(writeObject(value, path));
        return written;
    }

    private List<Object> writeList(Iterable<?> value, Type declaredType, String path, List<Annotation> annotations) {
        Type elementType = Object.class;
        if (declaredType instanceof ParameterizedType) {
            Type[] arguments = ((ParameterizedType) declaredType).getActualTypeArguments();
            if (arguments.length > 0) {
                elementType = arguments[0];
            }
        }
        List<Object> out = new ArrayList<Object>();
        int index = 0;
        for (Object item : value) {
            out.add(write(item, elementType, path + "[" + index + "]", annotations, item));
            index++;
        }
        return out;
    }

    private List<Object> writeArray(Object[] value, String path) {
        List<Object> out = new ArrayList<Object>();
        for (int index = 0; index < value.length; index++) {
            out.add(write(value[index], typeOf(value[index]), path + "[" + index + "]",
                    Collections.<Annotation>emptyList(), value[index]));
        }
        return out;
    }

    private Map<Object, Object> writeMap(Map<?, ?> value, String path) {
        Map<Object, Object> out = new LinkedHashMap<Object, Object>();
        for (Map.Entry<?, ?> entry : value.entrySet()) {
            Object key = write(entry.getKey());
            out.put(key, write(entry.getValue(), typeOf(entry.getValue()), path + "." + entry.getKey(),
                    Collections.<Annotation>emptyList(), entry.getValue()));
        }
        return out;
    }

    private Map<String, Object> writeObject(Object value, String path) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        for (Field field : introspector.fields(value.getClass())) {
            Object fieldValue = introspector.read(field, value);
            ConfigSerializer<Object> serializer = serializers.forField(field);
            String fieldKey = introspector.key(field);
            String fieldPath = path.isEmpty() ? fieldKey : path + "." + fieldKey;
            List<Annotation> annotations = Arrays.asList(field.getAnnotations());
            if (fieldValue != null && serializer != null) {
                out.put(fieldKey, write(serializer.serialize(fieldValue,
                        contexts.create(field.getGenericType(), fieldPath, annotations, fieldValue))));
            } else {
                out.put(fieldKey, write(fieldValue, field.getGenericType(), fieldPath, annotations, fieldValue));
            }
        }
        return out;
    }

    private static Type typeOf(Object value) {
        return value == null ? Object.class : value.getClass();
    }
}
