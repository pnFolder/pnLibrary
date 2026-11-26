package org.pnfolder.pnlibrary.localization.internal;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.pnfolder.pnlibrary.localization.TranslationException;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Reads a language file into a flat key-to-text map; accepts JSON objects and {@code key=value} lines. */
final class TranslationJsonCodec {
    private TranslationJsonCodec() {
    }

    /** Parses {@code bytes}; throws MALFORMED_DATA when the content is invalid or empty. */
    static Map<String, String> parse(byte[] bytes) {
        try {
            String text = new String(bytes, StandardCharsets.UTF_8);
            Map<String, String> values = isJson(text) ? parseJson(text) : parseLines(text);
            if (values.isEmpty()) {
                throw new IllegalArgumentException("Minecraft language table is empty");
            }
            return values;
        } catch (Exception error) {
            throw new TranslationException(
                    TranslationException.Reason.MALFORMED_DATA, "Malformed Minecraft language file", error);
        }
    }

    private static boolean isJson(String text) {
        return Whitespace.stripStart(text).startsWith("{");
    }

    private static Map<String, String> parseJson(String text) {
        Map<String, String> values = new LinkedHashMap<String, String>();
        for (Map.Entry<String, JsonElement> entry : JsonParser.parseString(text).getAsJsonObject().entrySet()) {
            values.put(entry.getKey(), entry.getValue().getAsString());
        }
        return values;
    }

    private static Map<String, String> parseLines(String text) {
        Map<String, String> values = new LinkedHashMap<String, String>();
        for (String raw : text.split("\r\n|\n|\r", -1)) {
            String line = Whitespace.strip(raw);
            int separator = line.indexOf('=');
            if (line.isEmpty() || line.charAt(0) == '#' || separator < 0) {
                continue;
            }
            values.put(Whitespace.strip(line.substring(0, separator)), Whitespace.strip(line.substring(separator + 1)));
        }
        return values;
    }
}
