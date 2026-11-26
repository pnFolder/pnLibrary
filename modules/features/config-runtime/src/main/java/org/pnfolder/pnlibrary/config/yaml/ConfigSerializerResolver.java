package org.pnfolder.pnlibrary.config.yaml;

import java.util.Objects;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.config.ConfigNodeRewriter;
import org.pnfolder.pnlibrary.api.config.ConfigSerializeWith;
import org.pnfolder.pnlibrary.api.config.ConfigSerializer;
import org.pnfolder.pnlibrary.api.config.ConfigTextParser;
import org.pnfolder.pnlibrary.common.lang.Failures;

/**
 * Resolves custom serializers declared by configuration types and fields.
 *
 * <p>Annotation serializers take precedence over serializers registered at runtime.
 * A serializer instance declared by an annotation is created once and then reused.
 */
public final class ConfigSerializerResolver {
    private final Supplier<Map<Class<?>, ConfigSerializer<?>>> registeredNow;
    private final Map<Class<?>, ConfigSerializer<?>> annotationInstances = new HashMap<>();
    private final Map<Class<?>, Object> textParsers = new HashMap<>();

    /** @param registeredNow the registered serializers, asked again on every use so that additions count */
    public ConfigSerializerResolver(@NotNull Supplier<Map<Class<?>, ConfigSerializer<?>>> registeredNow) {
        this.registeredNow = Objects.requireNonNull(registeredNow, "registeredNow");
    }

    /** Creates a resolver over a fixed set of serializers. */
    public ConfigSerializerResolver(@NotNull Map<Class<?>, ConfigSerializer<?>> registered) {
        this(() -> registered);
    }

    /** The serializer for {@code type}: an annotation on the class, then an exact registration, then a supertype one. */
    @Nullable
    @SuppressWarnings("unchecked")
    public ConfigSerializer<Object> forType(@NotNull Class<?> type) {
        ConfigSerializeWith annotation = type.getAnnotation(ConfigSerializeWith.class);
        if (annotation != null) {
            return (ConfigSerializer<Object>) annotationInstance(annotation.value());
        }
        Map<Class<?>, ConfigSerializer<?>> registered = registeredNow.get();
        ConfigSerializer<?> exact = registered.get(type);
        if (exact != null) {
            return (ConfigSerializer<Object>) exact;
        }
        for (Map.Entry<Class<?>, ConfigSerializer<?>> entry : registered.entrySet()) {
            if (entry.getKey().isAssignableFrom(type)) {
                return (ConfigSerializer<Object>) entry.getValue();
            }
        }
        return null;
    }

    /** The serializer named by {@code @ConfigSerializeWith} on {@code field}, or {@code null}. */
    @Nullable
    @SuppressWarnings("unchecked")
    public ConfigSerializer<Object> forField(@NotNull Field field) {
        ConfigSerializeWith annotation = field.getAnnotation(ConfigSerializeWith.class);
        return annotation == null ? null : (ConfigSerializer<Object>) annotationInstance(annotation.value());
    }

    /** The shared instance of the {@code @ConfigTextForm} parser {@code type}. */
    @NotNull
    @SuppressWarnings("unchecked")
    public ConfigTextParser<Object> textParser(@NotNull Class<? extends ConfigTextParser<?>> type) {
        return (ConfigTextParser<Object>) helper(type);
    }

    /** The shared instance of the {@code @ConfigNodeForm} rewriter {@code type}. */
    @NotNull
    public ConfigNodeRewriter nodeRewriter(@NotNull Class<? extends ConfigNodeRewriter> type) {
        return (ConfigNodeRewriter) helper(type);
    }

    // A Kotlin `object` is used as it is; any other class needs a no-argument constructor.
    private Object helper(Class<?> type) {
        synchronized (textParsers) {
            Object cached = textParsers.get(type);
            if (cached != null) {
                return cached;
            }
            Object instance = Failures.runCatchingNonFatal(() -> type.getField("INSTANCE").get(null)).getOrNull();
            if (instance == null) {
                instance = create(type);
            }
            textParsers.put(type, instance);
            return instance;
        }
    }

    private ConfigSerializer<?> annotationInstance(Class<? extends ConfigSerializer<?>> type) {
        synchronized (annotationInstances) {
            ConfigSerializer<?> cached = annotationInstances.get(type);
            if (cached == null) {
                cached = (ConfigSerializer<?>) create(type);
                annotationInstances.put(type, cached);
            }
            return cached;
        }
    }

    private static Object create(Class<?> type) {
        try {
            Constructor<?> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (java.lang.reflect.InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new IllegalStateException(cause);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(error);
        }
    }
}
