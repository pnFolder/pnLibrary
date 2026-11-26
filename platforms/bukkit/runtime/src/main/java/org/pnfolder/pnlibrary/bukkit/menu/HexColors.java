package org.pnfolder.pnlibrary.bukkit.menu;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Hex colours in item text, which servers before 1.16 cannot show. */
final class HexColors {
    private static final int HEX = 16;
    private static final Pattern HEX_CODE = Pattern.compile("§x(§[0-9A-Fa-f]){6}");
    private static final int[] BASIC_RGB = {
        0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
        0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF,
    };
    private static final String[] BASIC_CODES = {
        "§0", "§1", "§2", "§3", "§4", "§5", "§6", "§7", "§8", "§9", "§a", "§b", "§c", "§d", "§e", "§f",
    };

    /** {@code ChatColor.of} arrived together with hex colours in 1.16. */
    static final boolean SUPPORTED = detect();

    private HexColors() {
    }

    private static boolean detect() {
        try {
            Class.forName("net.md_5.bungee.api.ChatColor").getMethod("of", String.class);
            return true;
        } catch (ReflectiveOperationException | LinkageError error) {
            return false;
        }
    }

    /** On a server older than 1.16 replaces each hex colour in {@code text} with the nearest basic colour. */
    static String downgrade(String text) {
        if (SUPPORTED) {
            return text;
        }
        Matcher matcher = HEX_CODE.matcher(text);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String digits = matcher.group().replace("§", "").substring(1);
            matcher.appendReplacement(result, Matcher.quoteReplacement(nearest(Integer.parseInt(digits, HEX))));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String nearest(int rgb) {
        int best = 0;
        long bestDistance = Long.MAX_VALUE;
        for (int index = 0; index < BASIC_RGB.length; index++) {
            int color = BASIC_RGB[index];
            int red = (color >> 16 & 0xFF) - (rgb >> 16 & 0xFF);
            int green = (color >> 8 & 0xFF) - (rgb >> 8 & 0xFF);
            int blue = (color & 0xFF) - (rgb & 0xFF);
            long distance = (long) red * red + green * green + blue * blue;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = index;
            }
        }
        return BASIC_CODES[best];
    }
}
