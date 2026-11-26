package org.pnfolder.pnlibrary.api.currency;

/** Text checks shared by the currency models, with the whitespace rule of Kotlin's {@code isBlank()}. */
final class CurrencyText {
    private CurrencyText() {
    }

    /** Trims the same characters as Kotlin's {@code trim()}. */
    static String strip(String value) {
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

    static boolean isBlank(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (!isSpace(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isSpace(char c) {
        return Character.isWhitespace(c) || Character.isSpaceChar(c);
    }
}
