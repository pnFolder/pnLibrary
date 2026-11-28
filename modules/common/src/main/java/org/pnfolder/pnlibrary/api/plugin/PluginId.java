package org.pnfolder.pnlibrary.api.plugin;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Validated, platform-independent identity of a plugin using pnLibrary.
 *
 * <p>IDs are case-insensitive and normalized to lowercase. They may contain ASCII letters, digits, dots,
 * underscores, hyphens, and {@code @} (which the library uses in the key {@code plugin@module} of a module), must start with a letter or digit, and may not exceed 129 characters (a plugin and a module ID of 64 each, joined by {@code @}).
 */
public final class PluginId {
    private static final Pattern FORMAT = Pattern.compile("[a-z0-9][a-z0-9_.@-]{0,128}");

    private final String value;

    private PluginId(String value) {
        this.value = value;
    }

    /**
     * Creates a normalized ID or rejects an invalid value.
     *
     * @throws IllegalArgumentException when the value does not match the ID format
     */
    @NotNull
    public static PluginId of(@NotNull String value) {
        String normalized = strip(value).toLowerCase(Locale.ROOT);
        if (!FORMAT.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Plugin ID must match " + FORMAT.pattern());
        }
        return new PluginId(normalized);
    }

    /** Normalized lowercase identifier used as the registry key. */
    @NotNull
    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PluginId && value.equals(((PluginId) other).value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    /** Returns the normalized value for logs, diagnostics, and canonical references. */
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
