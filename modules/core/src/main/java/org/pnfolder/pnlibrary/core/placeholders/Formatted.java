package org.pnfolder.pnlibrary.core.placeholders;

import org.jetbrains.annotations.NotNull;

/** A value that brings the format to use when the address names none: from {@code Reply.value(x, "format")}. */
final class Formatted {
    private final Object value;
    private final String format;

    Formatted(@NotNull Object value, @NotNull String format) {
        this.value = value;
        this.format = format;
    }

    Object getValue() {
        return value;
    }

    String getFormat() {
        return format;
    }
}
