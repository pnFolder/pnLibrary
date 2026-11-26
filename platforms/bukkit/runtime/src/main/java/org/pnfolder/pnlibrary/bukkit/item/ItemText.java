package org.pnfolder.pnlibrary.bukkit.item;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Converts legacy ampersand colors and RGB colors ({@code &#RRGGBB}) to item text. */
final class ItemText {
    private static final Pattern HEX_COLOR = Pattern.compile("&#([0-9a-f]{6})", Pattern.CASE_INSENSITIVE);

    private ItemText() {
    }

    /** Preserves absent text. */
    static String color(String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = HEX_COLOR.matcher(text);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            StringBuilder code = new StringBuilder("§x");
            for (char digit : matcher.group(1).toCharArray()) {
                code.append('§').append(digit);
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(code.toString()));
        }
        matcher.appendTail(result);
        return result.toString().replace('&', '§');
    }
}
