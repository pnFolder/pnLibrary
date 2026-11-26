package org.pnfolder.pnlibrary.api.config;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Converts a custom type to and from YAML-compatible values.
 *
 * <p>{@link #serialize} should return {@code null}, a scalar, a list, or a string-keyed map. {@link #deserialize}
 * should reject invalid input with a descriptive exception; pnLibrary adds {@link ConfigSerializationContext#getPath()}
 * to surrounding configuration errors.
 *
 * <pre>{@code
 * final class DurationSerializer implements ConfigSerializer<Duration> {
 *     public Object serialize(Duration value, ConfigSerializationContext context) {
 *         return value.toString();
 *     }
 *
 *     public Duration deserialize(Object value, ConfigSerializationContext context) {
 *         return Duration.parse(String.valueOf(value));
 *     }
 * }
 * }</pre>
 */
public interface ConfigSerializer<T> {
    /** Converts {@code value} into a YAML-compatible representation. */
    @Nullable
    Object serialize(@NotNull T value, @NotNull ConfigSerializationContext context);

    /** Converts a parsed YAML {@code value} into the requested custom type. */
    @NotNull
    T deserialize(@Nullable Object value, @NotNull ConfigSerializationContext context);
}
