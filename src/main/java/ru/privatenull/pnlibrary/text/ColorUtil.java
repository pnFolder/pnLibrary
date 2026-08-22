package ru.privatenull.pnlibrary.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Map;

/** Parses pnFolder legacy, RGB and RGBA colors without MiniMessage. */
public final class ColorUtil {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('\u00A7')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();
    private static final Map<Character, TextColor> COLORS = Map.ofEntries(
            Map.entry('0', NamedTextColor.BLACK), Map.entry('1', NamedTextColor.DARK_BLUE),
            Map.entry('2', NamedTextColor.DARK_GREEN), Map.entry('3', NamedTextColor.DARK_AQUA),
            Map.entry('4', NamedTextColor.DARK_RED), Map.entry('5', NamedTextColor.DARK_PURPLE),
            Map.entry('6', NamedTextColor.GOLD), Map.entry('7', NamedTextColor.GRAY),
            Map.entry('8', NamedTextColor.DARK_GRAY), Map.entry('9', NamedTextColor.BLUE),
            Map.entry('a', NamedTextColor.GREEN), Map.entry('b', NamedTextColor.AQUA),
            Map.entry('c', NamedTextColor.RED), Map.entry('d', NamedTextColor.LIGHT_PURPLE),
            Map.entry('e', NamedTextColor.YELLOW), Map.entry('f', NamedTextColor.WHITE)
    );

    private ColorUtil() {
    }

    public static Component component(String value) {
        String input = value == null ? "" : value;
        TextComponent.Builder result = Component.text();
        StringBuilder text = new StringBuilder(input.length());
        Style.Builder style = Style.style();

        for (int index = 0; index < input.length();) {
            char marker = input.charAt(index);
            if (marker != '&' && marker != '\u00A7') {
                text.append(marker);
                index++;
                continue;
            }
            HexColor hex = readHex(input, index);
            if (hex != null) {
                flush(result, text, style);
                style = Style.style().color(hex.color());
                index += hex.length();
                continue;
            }
            if (index + 1 >= input.length()) {
                text.append(marker);
                index++;
                continue;
            }
            char code = Character.toLowerCase(input.charAt(index + 1));
            TextColor color = COLORS.get(code);
            if (color != null) {
                flush(result, text, style);
                style = Style.style().color(color);
                index += 2;
                continue;
            }
            TextDecoration decoration = decoration(code);
            if (decoration != null) {
                flush(result, text, style);
                style.decoration(decoration, true);
                index += 2;
                continue;
            }
            if (code == 'r') {
                flush(result, text, style);
                style = Style.style();
                index += 2;
                continue;
            }
            text.append(marker);
            index++;
        }
        flush(result, text, style);
        return result.build();
    }

    public static String colorize(String value) {
        return LEGACY.serialize(component(value));
    }

    private static HexColor readHex(String value, int index) {
        if (isAmpersandHex(value, index, 8)) {
            // Adventure/Minecraft text has no alpha channel. RRGGBBAA is accepted,
            // and AA is consumed while the visible RGB component is retained.
            return new HexColor(rgb(value, index + 2), 10);
        }
        if (isAmpersandHex(value, index, 6)) {
            return new HexColor(rgb(value, index + 2), 8);
        }
        if (isLegacyHex(value, index)) {
            StringBuilder hex = new StringBuilder(6);
            for (int part = index + 3; part <= index + 13; part += 2) hex.append(value.charAt(part));
            return new HexColor(TextColor.color(Integer.parseInt(hex.toString(), 16)), 14);
        }
        return null;
    }

    private static TextColor rgb(String value, int start) {
        return TextColor.color(Integer.parseInt(value.substring(start, start + 6), 16));
    }

    private static boolean isAmpersandHex(String value, int index, int digits) {
        if (value.charAt(index) != '&' || index + digits + 1 >= value.length()
                || value.charAt(index + 1) != '#') return false;
        for (int part = index + 2; part < index + 2 + digits; part++) {
            if (!isHex(value.charAt(part))) return false;
        }
        return true;
    }

    private static boolean isLegacyHex(String value, int index) {
        if (index + 13 >= value.length() || Character.toLowerCase(value.charAt(index + 1)) != 'x') return false;
        for (int part = index + 2; part <= index + 12; part += 2) {
            if ((value.charAt(part) != '&' && value.charAt(part) != '\u00A7')
                    || !isHex(value.charAt(part + 1))) return false;
        }
        return true;
    }

    private static TextDecoration decoration(char code) {
        return switch (code) {
            case 'k' -> TextDecoration.OBFUSCATED;
            case 'l' -> TextDecoration.BOLD;
            case 'm' -> TextDecoration.STRIKETHROUGH;
            case 'n' -> TextDecoration.UNDERLINED;
            case 'o' -> TextDecoration.ITALIC;
            default -> null;
        };
    }

    private static boolean isHex(char value) {
        return value >= '0' && value <= '9'
                || value >= 'a' && value <= 'f'
                || value >= 'A' && value <= 'F';
    }

    private static void flush(TextComponent.Builder result, StringBuilder text, Style.Builder style) {
        if (text.isEmpty()) return;
        result.append(Component.text(text.toString()).style(style.build()));
        text.setLength(0);
    }

    private record HexColor(TextColor color, int length) {
    }
}
