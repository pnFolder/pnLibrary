package ru.privatenull.pnlibrary.item;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Shared legacy and RGB item-text formatting. */
final class ItemText {
    private static final Pattern HEX = Pattern.compile("(?i)&#([0-9a-f]{6})");

    private ItemText() {
    }

    static String color(String text) {
        if (text == null) return null;
        Matcher matcher = HEX.matcher(text);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("\u00A7x");
            for (int index = 0; index < hex.length(); index++) {
                replacement.append('\u00A7').append(hex.charAt(index));
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement.toString()));
        }
        matcher.appendTail(result);
        return result.toString().replace('&', '\u00A7');
    }
}
