package org.pnfolder.pnlibrary.core.placeholders;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Reads {@code {group.branch.argument.field|format:arg|format}}. The braces are optional.
 *
 * <p>An owner may stand in front, ended by {@code @}: {@code {shop/main@shop.price}} (that module),
 * {@code {shop@shop.price}} (any module of the plugin), {@code {~@shop.price}} (the own plugin only),
 * {@code {~~@shop.price}} (the own module only), {@code {~~~@shop.price}} (the library only). A format takes an owner
 * the same way: {@code |shop/main@pad:4}, {@code |~~~@upper}.
 *
 * <p>A part may be a placeholder itself: {@code {clan.of.{player.name}.level}} reads the inner one first. A part that
 * holds a dot, a bar, a colon or a comma is put in quotes ({@code "a.b"}). {@code ~XX} stands for the character with
 * the hexadecimal code XX, the way PlaceholderAPI texts have to write {@code %} ({@code ~25}) and {@code "} ({@code ~22}).
 */
public final class Addresses {
    /** Placeholders inside placeholders go this deep; more is refused. */
    public static final int MAX_NESTING = 16;

    private static final Pattern ESCAPE = Pattern.compile("~([0-9A-Fa-f]{2})");
    private static final String QUOTED_CHARACTERS = ".|:,\"~{}";

    private Addresses() {
    }

    /** Takes the text apart; {@code null} when it is not an address. */
    public static @Nullable ParsedAddress parse(@NotNull String text) {
        String body = text.trim();
        if (body.startsWith("{") && body.endsWith("}")) {
            body = body.substring(1, body.length() - 1);
        }
        List<String> parts = split(body, '|');
        String path = parts.get(0);
        String owner = null;
        int at = path.indexOf('@');
        if (at >= 0 && noneOf(path.substring(0, at), ".\"")) {
            owner = path.substring(0, at).trim().toLowerCase(Locale.ROOT);
            if (owner.isEmpty()) {
                return null;
            }
            path = path.substring(at + 1);
        }
        List<String> words = parseWords(path);
        List<FormatCall> formats = parseFormats(parts.subList(1, parts.size()));
        return words == null || formats == null ? null : new ParsedAddress(words, formats, owner);
    }

    private static @Nullable List<String> parseWords(String path) {
        List<String> words = new ArrayList<String>();
        for (String word : split(path, '.')) {
            String decoded = decode(word);
            if (decoded.isEmpty()) {
                return null;
            }
            words.add(decoded);
        }
        return words;
    }

    private static @Nullable List<FormatCall> parseFormats(List<String> parts) {
        List<FormatCall> formats = new ArrayList<FormatCall>();
        for (String part : parts) {
            FormatCall call = parseFormat(part);
            if (call.getName().isEmpty()) {
                return null;
            }
            formats.add(call);
        }
        return formats;
    }

    private static FormatCall parseFormat(String part) {
        int colon = indexOfOutsideQuotes(part, ':');
        String name = (colon < 0 ? part : part.substring(0, colon)).trim();
        String formatOwner = null;
        int at = name.indexOf('@');
        if (at > 0) {
            formatOwner = name.substring(0, at).trim().toLowerCase(Locale.ROOT);
            name = name.substring(at + 1).trim();
        }
        List<String> arguments = new ArrayList<String>();
        if (colon >= 0) {
            for (String argument : split(part.substring(colon + 1), ',')) {
                arguments.add(decode(argument));
            }
        }
        return new FormatCall(name.toLowerCase(Locale.ROOT), arguments, formatOwner);
    }

    /**
     * The parts of an address that holds {@code {inner}} placeholders, with the braces of the inner ones removed;
     * {@code null} when it holds none. Quoted text is never taken apart.
     */
    public static @Nullable List<AddressSegment> innerParts(@NotNull String body) {
        List<AddressSegment> parts = new ArrayList<AddressSegment>();
        StringBuilder literal = new StringBuilder();
        boolean quoted = false;
        int index = 0;
        while (index < body.length()) {
            char character = body.charAt(index);
            if (character == '"') {
                quoted = !quoted;
            }
            if (character == '{' && !quoted) {
                int end = closingBrace(body, index);
                if (end >= body.length()) {
                    return null;
                }
                if (literal.length() > 0) {
                    parts.add(new AddressSegment(false, literal.toString()));
                }
                literal.setLength(0);
                parts.add(new AddressSegment(true, body.substring(index + 1, end)));
                index = end + 1;
                continue;
            }
            literal.append(character);
            index++;
        }
        return finish(parts, literal);
    }

    private static @Nullable List<AddressSegment> finish(List<AddressSegment> parts, StringBuilder literal) {
        if (parts.isEmpty()) {
            return null;
        }
        if (literal.length() > 0) {
            parts.add(new AddressSegment(false, literal.toString()));
        }
        return parts;
    }

    private static int closingBrace(String body, int open) {
        int depth = 0;
        int end = open;
        while (end < body.length()) {
            char character = body.charAt(end);
            if (character == '{') {
                depth++;
            } else if (character == '}' && --depth == 0) {
                break;
            }
            end++;
        }
        return end;
    }

    /** How deep {@code {}} pairs lie inside each other in {@code body} (0 when none); quoted text does not count. */
    public static int depth(@NotNull String body) {
        boolean quoted = false;
        int now = 0;
        int deepest = 0;
        for (int i = 0; i < body.length(); i++) {
            char character = body.charAt(i);
            if (character == '"') {
                quoted = !quoted;
            } else if (quoted) {
                continue;
            } else if (character == '{') {
                deepest = Math.max(deepest, ++now);
            } else if (character == '}') {
                now = Math.max(0, now - 1);
            }
        }
        return deepest;
    }

    /** Writes {@code text} so that {@link #parse} reads it back as one part. */
    public static @NotNull String quote(@NotNull String text) {
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (QUOTED_CHARACTERS.indexOf(character) >= 0 || Character.isWhitespace(character) || Character.isSpaceChar(character)) {
                return "\"" + text.replace("\"", "~22") + "\"";
            }
        }
        return text;
    }

    private static boolean noneOf(String text, String characters) {
        for (int i = 0; i < text.length(); i++) {
            if (characters.indexOf(text.charAt(i)) >= 0) {
                return false;
            }
        }
        return true;
    }

    private static List<String> split(String text, char separator) {
        List<String> parts = new ArrayList<String>();
        boolean quoted = false;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (character == '"') {
                quoted = !quoted;
                current.append(character);
            } else if (character == separator && !quoted) {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(character);
            }
        }
        parts.add(current.toString());
        return Collections.unmodifiableList(parts);
    }

    private static int indexOfOutsideQuotes(String text, char wanted) {
        boolean quoted = false;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '"') {
                quoted = !quoted;
            } else if (character == wanted && !quoted) {
                return index;
            }
        }
        return -1;
    }

    private static String decode(String part) {
        String trimmed = part.trim();
        boolean quoted = trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"");
        String inner = quoted ? trimmed.substring(1, trimmed.length() - 1) : trimmed;
        Matcher matcher = ESCAPE.matcher(inner);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            char decoded = (char) Integer.parseInt(matcher.group(1), 16);
            matcher.appendReplacement(result, Matcher.quoteReplacement(String.valueOf(decoded)));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
