package org.pnfolder.pnlibrary.currency;

import java.util.Locale;
import java.util.regex.Pattern;

import org.jetbrains.annotations.NotNull;

/** Spelling rules of currency names and aliases. */
final class CurrencyNames {
    private static final Pattern VALID = Pattern.compile("[a-z0-9_.-]+");

    private CurrencyNames() {
    }

    /** Lowercases and validates a currency name or alias ({@code a-z}, digits, {@code _}, {@code .}, {@code -}). */
    @NotNull
    static String normalize(@NotNull String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!VALID.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid currency name: " + value);
        }
        return normalized;
    }

    /** Whether {@code text} is empty or holds only whitespace. */
    static boolean isBlank(@NotNull String text) {
        for (int index = 0; index < text.length(); index++) {
            char symbol = text.charAt(index);
            if (!Character.isWhitespace(symbol) && !Character.isSpaceChar(symbol)) {
                return false;
            }
        }
        return true;
    }
}
