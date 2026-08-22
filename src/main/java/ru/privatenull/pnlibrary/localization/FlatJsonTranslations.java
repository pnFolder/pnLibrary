package ru.privatenull.pnlibrary.localization;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Minimal parser for Minecraft's flat string-to-string language JSON files. */
final class FlatJsonTranslations {
    private final String source;
    private int index;

    private FlatJsonTranslations(String source) {
        this.source = source;
    }

    static Map<String, String> read(InputStream input) throws IOException {
        if (input == null) throw new IOException("Missing translation resource");
        String source = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        return new FlatJsonTranslations(source).parse();
    }

    private Map<String, String> parse() throws IOException {
        Map<String, String> values = new LinkedHashMap<>();
        whitespace();
        expect('{');
        whitespace();
        if (take('}')) return values;
        while (true) {
            String key = string();
            whitespace();
            expect(':');
            whitespace();
            String value = string();
            values.put(key, value);
            whitespace();
            if (take('}')) return values;
            expect(',');
            whitespace();
        }
    }

    private String string() throws IOException {
        expect('"');
        StringBuilder value = new StringBuilder();
        while (index < source.length()) {
            char current = source.charAt(index++);
            if (current == '"') return value.toString();
            if (current != '\\') {
                value.append(current);
                continue;
            }
            if (index >= source.length()) throw error("Unfinished escape sequence");
            char escaped = source.charAt(index++);
            switch (escaped) {
                case '"', '\\', '/' -> value.append(escaped);
                case 'b' -> value.append('\b');
                case 'f' -> value.append('\f');
                case 'n' -> value.append('\n');
                case 'r' -> value.append('\r');
                case 't' -> value.append('\t');
                case 'u' -> value.append(unicode());
                default -> throw error("Unknown escape sequence: \\" + escaped);
            }
        }
        throw error("Unterminated string");
    }

    private char unicode() throws IOException {
        if (index + 4 > source.length()) throw error("Unfinished unicode escape");
        String hex = source.substring(index, index + 4);
        index += 4;
        try {
            return (char) Integer.parseInt(hex, 16);
        } catch (NumberFormatException exception) {
            throw error("Invalid unicode escape: " + hex);
        }
    }

    private void whitespace() {
        while (index < source.length() && Character.isWhitespace(source.charAt(index))) index++;
    }

    private boolean take(char expected) {
        if (index >= source.length() || source.charAt(index) != expected) return false;
        index++;
        return true;
    }

    private void expect(char expected) throws IOException {
        if (!take(expected)) throw error("Expected '" + expected + "'");
    }

    private IOException error(String message) {
        return new IOException(message + " at character " + index);
    }
}
