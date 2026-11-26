package org.pnfolder.pnlibrary.api.config;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** A value read from the file, with small helpers for the string and number changes migrations need. */
public final class Value {
    private final Object raw;

    Value(@Nullable Object raw) {
        this.raw = raw;
    }

    /** Text before the first {@code separator}; the whole text when the separator is absent. */
    @NotNull
    public Value before(@NotNull String separator) {
        String text = text();
        int index = text.indexOf(separator);
        return new Value(index < 0 ? text : text.substring(0, index));
    }

    /** Text after the first {@code separator}; the whole text when the separator is absent. */
    @NotNull
    public Value after(@NotNull String separator) {
        String text = text();
        int index = text.indexOf(separator);
        return new Value(index < 0 ? text : text.substring(index + separator.length()));
    }

    /** Whole number from the text. Fails with a readable message for anything else. */
    public int toInt() {
        String text = text();
        try {
            return Integer.parseInt(ConfigText.strip(text));
        } catch (NumberFormatException error) {
            throw new IllegalStateException("'" + text + "' is not a whole number");
        }
    }

    @Nullable
    Object unwrap() {
        return raw;
    }

    private String text() {
        if (raw == null) {
            throw new IllegalStateException("The value is empty");
        }
        return raw.toString();
    }
}
