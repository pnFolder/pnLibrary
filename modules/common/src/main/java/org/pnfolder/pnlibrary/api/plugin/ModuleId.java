package org.pnfolder.pnlibrary.api.plugin;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.regex.Pattern;

/** Validated, case-insensitive local identity of a logical module. */
public final class ModuleId {
    private static final Pattern FORMAT = Pattern.compile("[a-z0-9][a-z0-9_.-]{0,63}");

    private final String value;

    private ModuleId(String value) {
        this.value = value;
    }

    /**
     * Normalizes and validates the value.
     *
     * @throws IllegalArgumentException when the value does not match the ID format
     */
    @NotNull
    public static ModuleId of(@NotNull String value) {
        String normalized = strip(value).toLowerCase(Locale.ROOT);
        if (!FORMAT.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Module ID must match " + FORMAT.pattern());
        }
        return new ModuleId(normalized);
    }

    /** Normalized lowercase module identifier. */
    @NotNull
    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ModuleId && value.equals(((ModuleId) other).value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    /** Returns the normalized identifier without additional formatting. */
    @Override
    public String toString() {
        return value;
    }

    // Same whitespace rule as Kotlin's trim(), which also strips non-ASCII spaces such as NBSP.
    private static String strip(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && isSpace(value.charAt(start))) {
            start++;
        }
        while (end > start && isSpace(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(start, end);
    }

    private static boolean isSpace(char c) {
        return Character.isWhitespace(c) || Character.isSpaceChar(c);
    }
}
