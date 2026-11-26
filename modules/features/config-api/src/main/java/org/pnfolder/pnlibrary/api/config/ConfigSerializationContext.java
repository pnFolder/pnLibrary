package org.pnfolder.pnlibrary.api.config;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Objects;

/**
 * Metadata for one value being processed by a {@link ConfigSerializer}.
 *
 * <p>For a field such as {@code database.timeout}, {@link #getDeclaredType()} retains generic information while
 * {@link #getRawType()} is the erased JVM class. {@link #getDefaultValue()} is useful when a serializer accepts a
 * compact representation but needs code-defined fallback metadata.
 */
public final class ConfigSerializationContext {
    private final String path;
    private final Type declaredType;
    private final Class<?> rawType;
    private final List<Annotation> annotations;
    private final Object defaultValue;
    private final ConfigValueDecoder decoder;
    private final ConfigValueEncoder encoder;

    /** Creates a context for use outside a configuration load or save. */
    public ConfigSerializationContext(@NotNull String path, @NotNull Type declaredType, @NotNull Class<?> rawType,
                                      @NotNull List<Annotation> annotations, @Nullable Object defaultValue) {
        this(path, declaredType, rawType, annotations, defaultValue, null, null);
    }

    /**
     * Creates a context.
     *
     * @param path full dot-separated YAML path of the value
     * @param declaredType reflective declared type, including generic arguments when available
     * @param rawType erased JVM class corresponding to the declared type
     * @param annotations runtime annotations declared on the field being processed
     * @param defaultValue value supplied by the code-defined default model, or {@code null}
     * @param decoder reads a nested value the way the configuration does, or {@code null} outside a load
     * @param encoder writes a nested value the way the configuration does, or {@code null} outside a save
     */
    public ConfigSerializationContext(@NotNull String path, @NotNull Type declaredType, @NotNull Class<?> rawType,
                                      @NotNull List<Annotation> annotations, @Nullable Object defaultValue,
                                      @Nullable ConfigValueDecoder decoder, @Nullable ConfigValueEncoder encoder) {
        this.path = Objects.requireNonNull(path, "path");
        this.declaredType = Objects.requireNonNull(declaredType, "declaredType");
        this.rawType = Objects.requireNonNull(rawType, "rawType");
        this.annotations = Objects.requireNonNull(annotations, "annotations");
        this.defaultValue = defaultValue;
        this.decoder = decoder;
        this.encoder = encoder;
    }

    /** Full dot-separated YAML path of the value. */
    @NotNull
    public String getPath() {
        return path;
    }

    /** Reflective declared type, including generic arguments when available. */
    @NotNull
    public Type getDeclaredType() {
        return declaredType;
    }

    /** Erased JVM class corresponding to the declared type. */
    @NotNull
    public Class<?> getRawType() {
        return rawType;
    }

    /** Runtime annotations declared on the field being processed. */
    @NotNull
    public List<Annotation> getAnnotations() {
        return annotations;
    }

    /** Value supplied by the code-defined default model, or {@code null}. */
    @Nullable
    public Object getDefaultValue() {
        return defaultValue;
    }

    /** Reads a nested value the way the configuration does, or {@code null} when used outside a configuration load. */
    @Nullable
    public ConfigValueDecoder getDecoder() {
        return decoder;
    }

    /** Writes a nested value the way the configuration does, or {@code null} when used outside a configuration save. */
    @Nullable
    public ConfigValueEncoder getEncoder() {
        return encoder;
    }

    /** Returns the first field annotation assignable to {@code type}, or {@code null}. */
    @Nullable
    public <A extends Annotation> A annotation(@NotNull Class<A> type) {
        for (Annotation annotation : annotations) {
            if (type.isInstance(annotation)) {
                return type.cast(annotation);
            }
        }
        return null;
    }

    /**
     * Reads {@code value}, found at {@code path}, as {@code type} with every rule of the configuration: polymorphic
     * {@code type}, text forms, custom serializers and error locations.
     *
     * @throws IllegalStateException when this context has no decoder
     */
    @NotNull
    public <T> T decode(@Nullable Object value, @NotNull Class<T> type, @NotNull String path) {
        if (decoder == null) {
            throw new IllegalStateException("Nested values cannot be read here");
        }
        return type.cast(decoder.decode(value, type, path));
    }

    /**
     * Writes {@code value} as {@code type} with every rule of the configuration, including the {@code type} of
     * polymorphic values.
     *
     * @throws IllegalStateException when this context has no encoder
     */
    @Nullable
    public Object encode(@Nullable Object value, @NotNull Type type, @NotNull String path) {
        if (encoder == null) {
            throw new IllegalStateException("Nested values cannot be written here");
        }
        return encoder.encode(value, type, path);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ConfigSerializationContext)) {
            return false;
        }
        ConfigSerializationContext that = (ConfigSerializationContext) other;
        return path.equals(that.path) && declaredType.equals(that.declaredType) && rawType.equals(that.rawType)
                && annotations.equals(that.annotations) && Objects.equals(defaultValue, that.defaultValue)
                && Objects.equals(decoder, that.decoder) && Objects.equals(encoder, that.encoder);
    }

    @Override
    public int hashCode() {
        return Objects.hash(path, declaredType, rawType, annotations, defaultValue, decoder, encoder);
    }

    @Override
    public String toString() {
        return "ConfigSerializationContext(path=" + path + ", declaredType=" + declaredType + ", rawType=" + rawType
                + ", annotations=" + annotations + ", defaultValue=" + defaultValue + ", decoder=" + decoder
                + ", encoder=" + encoder + ")";
    }
}
