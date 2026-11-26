package org.pnfolder.pnlibrary.core.menu.style;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;

/**
 * Colours of the menu text, written as the {@code §x§R§R§G§G§B§B} form that every platform understands.
 *
 * <p>A server older than 1.16 does not know the form; its adapter turns it into the nearest basic colour before it
 * shows the text, so the menu can be built once and shown anywhere.
 */
public final class MenuColors {
    public static final char SECTION = '§';

    private static final int RADIX = 16;
    private static final int[] SHIFTS = {16, 8, 0};
    private static final Pattern HEX = Pattern.compile("&#([0-9A-Fa-f]{6})");

    private MenuColors() {
    }

    public static @NotNull String color(@NotNull String template) {
        Matcher matcher = HEX.matcher(template);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement(hex(matcher.group(1))));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    public static @NotNull String hex(@NotNull String rgb) {
        StringBuilder text = new StringBuilder().append(SECTION).append('x');
        for (int index = 0; index < rgb.length(); index++) {
            text.append(SECTION).append(rgb.charAt(index));
        }
        return text.toString();
    }

    public static @NotNull String legacy(char code) {
        return String.valueOf(SECTION) + code;
    }

    public static @NotNull String gradient(@NotNull String text, @NotNull String from, @NotNull String to) {
        if (text.length() < 2) {
            return hex(from) + text;
        }
        int start = Integer.parseInt(from, RADIX);
        int end = Integer.parseInt(to, RADIX);
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < text.length(); index++) {
            double step = (double) index / (text.length() - 1);
            int[] rgb = new int[SHIFTS.length];
            for (int part = 0; part < SHIFTS.length; part++) {
                int a = (start >> SHIFTS[part]) & 0xFF;
                int b = (end >> SHIFTS[part]) & 0xFF;
                rgb[part] = (int) (a + (b - a) * step);
            }
            result.append(hex(String.format(Locale.ROOT, "%02X%02X%02X", rgb[0], rgb[1], rgb[2]))).append(text.charAt(index));
        }
        return result.toString();
    }
}
