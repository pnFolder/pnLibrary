package org.pnfolder.pnlibrary.config.yaml.text;

import org.jetbrains.annotations.NotNull;

/** Turns a YAML scalar into the Java scalar a field declares. */
public final class ScalarConversion {
    /** Returned when the target type is not a scalar this class knows. */
    public static final Object NOT_SCALAR = new Object();

    private ScalarConversion() {
    }

    /** Converts {@code value} to {@code rawType}, or returns {@link #NOT_SCALAR}. */
    @NotNull
    public static Object convert(@NotNull Class<?> rawType, @NotNull Object value) {
        if (rawType == String.class) {
            return value.toString();
        }
        if (rawType == boolean.class || rawType == Boolean.class) {
            return (Boolean) value;
        }
        if (rawType == byte.class || rawType == Byte.class) {
            return ((Number) value).byteValue();
        }
        if (rawType == short.class || rawType == Short.class) {
            return ((Number) value).shortValue();
        }
        if (rawType == int.class || rawType == Integer.class) {
            return ((Number) value).intValue();
        }
        if (rawType == long.class || rawType == Long.class) {
            return ((Number) value).longValue();
        }
        if (rawType == float.class || rawType == Float.class) {
            return ((Number) value).floatValue();
        }
        if (rawType == double.class || rawType == Double.class) {
            return ((Number) value).doubleValue();
        }
        if (rawType.isEnum()) {
            for (Object constant : rawType.getEnumConstants()) {
                if (((Enum<?>) constant).name().equalsIgnoreCase(value.toString())) {
                    return constant;
                }
            }
            throw new IllegalArgumentException("Unknown " + rawType.getSimpleName() + " value: " + value);
        }
        return NOT_SCALAR;
    }
}
