package org.pnfolder.pnlibrary.core.placeholders.format;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/** A value that brings the format to use when the address names none: from {@code Reply.value(x, "format")}. */
public final class Formatted {
    private final Object value;
    private final String format;

    public Formatted(@NotNull Object value, @NotNull String format) {
        this.value = Objects.requireNonNull(value, "value");
        this.format = Objects.requireNonNull(format, "format");
    }

    public Object getValue() {
        return value;
    }

    public String getFormat() {
        return format;
    }
}
