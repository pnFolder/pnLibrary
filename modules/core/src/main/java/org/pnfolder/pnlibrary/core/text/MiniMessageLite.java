package org.pnfolder.pnlibrary.core.text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.jetbrains.annotations.NotNull;

/**
 * MiniMessage subset for servers whose own Adventure is too old for the real one. It only calls long-standing
 * Adventure methods ({@code Component.text}, {@code color}, {@code decoration}, {@code append}). Colors by name,
 * {@code #rrggbb}, {@code color:}, decorations, {@code reset}, {@code br}/{@code newline} and escaping with a
 * backslash match MiniMessage; unknown tags stay plain text. {@code gradient}, {@code transition} and
 * {@code rainbow} degrade to their first color or to no color.
 */
public final class MiniMessageLite {
    private static final Map<String, TextColor> COLORS = new HashMap<String, TextColor>();
    private static final Map<String, TextDecoration> DECORATIONS = new HashMap<String, TextDecoration>();
    private static final Set<String> COLOR_PREFIXES = new HashSet<String>(Arrays.asList("color", "c", "colour"));
    private static final Set<String> FADES = new HashSet<String>(Arrays.asList("gradient", "transition", "rainbow"));

    static {
        COLORS.put("black", NamedTextColor.BLACK);
        COLORS.put("dark_blue", NamedTextColor.DARK_BLUE);
        COLORS.put("dark_green", NamedTextColor.DARK_GREEN);
        COLORS.put("dark_aqua", NamedTextColor.DARK_AQUA);
        COLORS.put("dark_red", NamedTextColor.DARK_RED);
        COLORS.put("dark_purple", NamedTextColor.DARK_PURPLE);
        COLORS.put("gold", NamedTextColor.GOLD);
        COLORS.put("gray", NamedTextColor.GRAY);
        COLORS.put("grey", NamedTextColor.GRAY);
        COLORS.put("dark_gray", NamedTextColor.DARK_GRAY);
        COLORS.put("dark_grey", NamedTextColor.DARK_GRAY);
        COLORS.put("blue", NamedTextColor.BLUE);
        COLORS.put("green", NamedTextColor.GREEN);
        COLORS.put("aqua", NamedTextColor.AQUA);
        COLORS.put("red", NamedTextColor.RED);
        COLORS.put("light_purple", NamedTextColor.LIGHT_PURPLE);
        COLORS.put("yellow", NamedTextColor.YELLOW);
        COLORS.put("white", NamedTextColor.WHITE);
        for (String name : new String[] {"bold", "b"}) {
            DECORATIONS.put(name, TextDecoration.BOLD);
        }
        for (String name : new String[] {"italic", "i", "em"}) {
            DECORATIONS.put(name, TextDecoration.ITALIC);
        }
        for (String name : new String[] {"underlined", "u"}) {
            DECORATIONS.put(name, TextDecoration.UNDERLINED);
        }
        for (String name : new String[] {"strikethrough", "st"}) {
            DECORATIONS.put(name, TextDecoration.STRIKETHROUGH);
        }
        for (String name : new String[] {"obfuscated", "obf"}) {
            DECORATIONS.put(name, TextDecoration.OBFUSCATED);
        }
    }

    private MiniMessageLite() {
    }

    public static @NotNull Component deserialize(@NotNull String input) {
        List<LiteTag> stack = new ArrayList<LiteTag>();
        StringBuilder text = new StringBuilder();
        List<Component> parts = new ArrayList<Component>();
        int i = 0;
        while (i < input.length()) {
            char c = input.charAt(i);
            if (c == '\\' && input.startsWith("<", i + 1)) {
                text.append('<');
                i += 2;
                continue;
            }
            int end = c == '<' ? tagEnd(input, i) : -1;
            if (end < 0 || !apply(input.substring(i + 1, end), stack, text, parts)) {
                text.append(c);
                i++;
                continue;
            }
            i = end + 1;
        }
        flush(text, stack, parts);
        TextComponent.Builder builder = Component.text();
        for (Component part : parts) {
            builder.append(part);
        }
        return builder.build();
    }

    private static int tagEnd(String input, int start) {
        for (int j = start + 1; j < input.length(); j++) {
            if (input.charAt(j) == '>') {
                return j;
            }
            if (input.charAt(j) == '<') {
                return -1;
            }
        }
        return -1;
    }

    /** Returns false for an unknown tag or a closing tag with nothing open, so the caller keeps it as text. */
    private static boolean apply(String tag, List<LiteTag> stack, StringBuilder text, List<Component> parts) {
        String lower = tag.toLowerCase(Locale.ROOT);
        boolean closing = lower.startsWith("/");
        boolean negated = lower.startsWith("!");
        String body = removePrefix(removePrefix(lower, "/"), "!");
        String[] args = body.split(":", -1);
        String name = args[0];
        if (!closing && !negated && (name.equals("br") || name.equals("newline"))) {
            flush(text, stack, parts);
            parts.add(Component.text("\n"));
            return true;
        }
        if (!closing && !negated && name.equals("reset")) {
            flush(text, stack, parts);
            stack.clear();
            return true;
        }
        LiteTag entry = entryFor(name, args, negated, closing);
        if (entry == null) {
            return false;
        }
        int index = closing ? lastIndexOf(stack, entry.name) : -1;
        if (closing && index < 0) {
            return false;
        }
        flush(text, stack, parts);
        if (closing) {
            while (stack.size() > index) {
                stack.remove(stack.size() - 1);
            }
        } else {
            stack.add(entry);
        }
        return true;
    }

    private static String removePrefix(String value, String prefix) {
        return value.startsWith(prefix) ? value.substring(prefix.length()) : value;
    }

    private static int lastIndexOf(List<LiteTag> stack, String name) {
        for (int i = stack.size() - 1; i >= 0; i--) {
            if (stack.get(i).name.equals(name)) {
                return i;
            }
        }
        return -1;
    }

    private static LiteTag entryFor(String name, String[] args, boolean negated, boolean closing) {
        TextDecoration decoration = DECORATIONS.get(name);
        if (decoration != null) {
            return new LiteTag(decoration.name(), null, decoration, !negated);
        }
        if (negated) {
            return null;
        }
        if (COLOR_PREFIXES.contains(name) && closing) {
            return new LiteTag("color", null, null, false);
        }
        if (COLOR_PREFIXES.contains(name)) {
            TextColor color = parseColor(args.length > 1 ? args[1] : null);
            return color == null ? null : new LiteTag("color", color, null, false);
        }
        if (FADES.contains(name)) {
            for (int i = 1; i < args.length; i++) {
                TextColor color = parseColor(args[i]);
                if (color != null) {
                    return new LiteTag(name, color, null, false);
                }
            }
            return new LiteTag(name, null, null, false);
        }
        TextColor color = parseColor(name);
        return color == null ? null : new LiteTag(name, color, null, false);
    }

    private static TextColor parseColor(String value) {
        if (value == null) {
            return null;
        }
        TextColor named = COLORS.get(value);
        if (named != null) {
            return named;
        }
        String hex = removePrefix(value, "#");
        if (!value.startsWith("#") || hex.length() != 6) {
            return null;
        }
        for (int i = 0; i < hex.length(); i++) {
            if (Character.digit(hex.charAt(i), 16) < 0) {
                return null;
            }
        }
        return TextColor.color(Integer.parseInt(hex, 16));
    }

    private static void flush(StringBuilder text, List<LiteTag> stack, List<Component> parts) {
        if (text.length() == 0) {
            return;
        }
        Component part = Component.text(text.toString());
        for (int i = stack.size() - 1; i >= 0; i--) {
            if (stack.get(i).color != null) {
                part = part.color(stack.get(i).color);
                break;
            }
        }
        Map<TextDecoration, Boolean> decorations = new LinkedHashMap<TextDecoration, Boolean>();
        for (LiteTag tag : stack) {
            if (tag.decoration != null) {
                decorations.put(tag.decoration, tag.state);
            }
        }
        for (Map.Entry<TextDecoration, Boolean> entry : decorations.entrySet()) {
            part = part.decoration(entry.getKey(), entry.getValue());
        }
        parts.add(part);
        text.setLength(0);
    }
}
