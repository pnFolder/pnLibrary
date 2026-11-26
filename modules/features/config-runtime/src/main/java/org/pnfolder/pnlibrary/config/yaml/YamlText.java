package org.pnfolder.pnlibrary.config.yaml;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Text operations with the whitespace rules the YAML code was written against. */
final class YamlText {
    private static final Pattern LINE_BREAK = Pattern.compile("\r\n|\n|\r");

    private YamlText() {
    }

    static boolean isWhitespace(char symbol) {
        return Character.isWhitespace(symbol) || Character.isSpaceChar(symbol);
    }

    static boolean isBlank(String text) {
        for (int index = 0; index < text.length(); index++) {
            if (!isWhitespace(text.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    static String trim(String text) {
        return trimStart(trimEnd(text));
    }

    static String trimStart(String text) {
        int start = 0;
        while (start < text.length() && isWhitespace(text.charAt(start))) {
            start++;
        }
        return text.substring(start);
    }

    static String trimEnd(String text) {
        int end = text.length();
        while (end > 0 && isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(0, end);
    }

    /** Removes the given symbols from both ends. */
    static String trimChars(String text, char first, char second) {
        int start = 0;
        int end = text.length();
        while (start < end && (text.charAt(start) == first || text.charAt(start) == second)) {
            start++;
        }
        while (end > start && (text.charAt(end - 1) == first || text.charAt(end - 1) == second)) {
            end--;
        }
        return text.substring(start, end);
    }

    /** Splits at {@code \n}, {@code \r\n} and {@code \r}; a trailing break leaves a last empty line. */
    static List<String> lines(String text) {
        List<String> lines = new ArrayList<String>();
        int from = 0;
        java.util.regex.Matcher matcher = LINE_BREAK.matcher(text);
        while (matcher.find()) {
            lines.add(text.substring(from, matcher.start()));
            from = matcher.end();
        }
        lines.add(text.substring(from));
        return lines;
    }

    /** The number of leading spaces. */
    static int indentOf(String line) {
        int indent = 0;
        while (indent < line.length() && line.charAt(indent) == ' ') {
            indent++;
        }
        return indent;
    }

    static String spaces(int count) {
        StringBuilder spaces = new StringBuilder(count);
        for (int index = 0; index < count; index++) {
            spaces.append(' ');
        }
        return spaces.toString();
    }
}
