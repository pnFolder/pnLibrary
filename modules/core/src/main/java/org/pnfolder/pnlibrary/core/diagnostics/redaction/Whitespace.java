package org.pnfolder.pnlibrary.core.diagnostics.redaction;

import org.jetbrains.annotations.NotNull;

/**
 * Whitespace rules for report text. Java's {@code String.trim()} and Kotlin's {@code trim()} disagree on which
 * characters are blank, and report data was always cut by the Kotlin rule, so it stays that way.
 */
public final class Whitespace {
    private Whitespace() {
    }

    /** Whether {@code value} is a whitespace or space separator character. */
    public static boolean isBlank(char value) {
        return Character.isWhitespace(value) || Character.isSpaceChar(value);
    }

    /** {@code text} without leading and trailing whitespace. */
    @NotNull
    public static String trim(@NotNull String text) {
        int start = 0;
        int end = text.length();
        while (start < end && isBlank(text.charAt(start))) {
            start++;
        }
        while (end > start && isBlank(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(start, end);
    }

    /** {@code text} without leading whitespace. */
    @NotNull
    public static String trimStart(@NotNull String text) {
        int start = 0;
        while (start < text.length() && isBlank(text.charAt(start))) {
            start++;
        }
        return text.substring(start);
    }

    /** Whether {@code text} is empty or only whitespace. */
    public static boolean isBlank(@NotNull String text) {
        for (int index = 0; index < text.length(); index++) {
            if (!isBlank(text.charAt(index))) {
                return false;
            }
        }
        return true;
    }
}
