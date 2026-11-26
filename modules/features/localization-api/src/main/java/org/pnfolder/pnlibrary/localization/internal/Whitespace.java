package org.pnfolder.pnlibrary.localization.internal;

/** Whitespace rule shared by the parsers: the same as Kotlin's {@code trim()}, which also strips NBSP and similar. */
final class Whitespace {
    private Whitespace() {
    }

    static String strip(String value) {
        int end = value.length();
        int start = indexAfterSpaces(value, 0);
        while (end > start && isSpace(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(start, end);
    }

    static String stripStart(String value) {
        return value.substring(indexAfterSpaces(value, 0));
    }

    private static int indexAfterSpaces(String value, int from) {
        int start = from;
        while (start < value.length() && isSpace(value.charAt(start))) {
            start++;
        }
        return start;
    }

    private static boolean isSpace(char c) {
        return Character.isWhitespace(c) || Character.isSpaceChar(c);
    }
}
