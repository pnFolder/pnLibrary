package org.pnfolder.pnlibrary.core.text;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;

/** Rewrites legacy {@code &} codes, hex forms and old gradients of adaptive text into MiniMessage tags. */
final class LegacyToMini {
    private static final Pattern OLD_GRADIENT = Pattern.compile("<&?#([0-9a-fA-F]{6})>([^<]*)</&?#([0-9a-fA-F]{6})>");
    /** Bukkit hex form {@code &x&f&f&0&0&0&0} (and its section-sign twin): six single digits behind {@code x}. */
    private static final Pattern X_HEX = Pattern.compile("(?i)&x((?:&[0-9a-f]){6})");
    private static final Pattern HEX = Pattern.compile("(?i)(?<!<)&?#([0-9a-f]{6})(?![^<]*>)");
    private static final Pattern LEGACY = Pattern.compile("&([0-9a-fk-or])", Pattern.CASE_INSENSITIVE);
    private static final Map<String, String> LEGACY_TAGS = new HashMap<String, String>();

    static {
        String[] names = {"black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray",
            "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white"};
        for (int i = 0; i < names.length; i++) {
            LEGACY_TAGS.put(Integer.toHexString(i), "<" + names[i] + ">");
        }
        LEGACY_TAGS.put("k", "<obfuscated>");
        LEGACY_TAGS.put("l", "<bold>");
        LEGACY_TAGS.put("m", "<strikethrough>");
        LEGACY_TAGS.put("n", "<underlined>");
        LEGACY_TAGS.put("o", "<italic>");
        LEGACY_TAGS.put("r", "<reset>");
    }

    private LegacyToMini() {
    }

    static @NotNull String convert(@NotNull String source) {
        String value = source.replace('§', '&');
        value = replace(OLD_GRADIENT, value, new Rewrite() {
            @Override
            public String apply(Matcher m) {
                return "<gradient:#" + m.group(1) + ":#" + m.group(3) + ">" + m.group(2) + "</gradient>";
            }
        });
        value = replace(X_HEX, value, new Rewrite() {
            @Override
            public String apply(Matcher m) {
                return "<#" + m.group(1).replace("&", "") + ">";
            }
        });
        value = replace(HEX, value, new Rewrite() {
            @Override
            public String apply(Matcher m) {
                return "<#" + m.group(1) + ">";
            }
        });
        return replace(LEGACY, value, new Rewrite() {
            @Override
            public String apply(Matcher m) {
                String tag = LEGACY_TAGS.get(m.group(1).toLowerCase(Locale.ROOT));
                return tag != null ? tag : m.group();
            }
        });
    }

    private static String replace(Pattern pattern, String input, Rewrite rewrite) {
        Matcher matcher = pattern.matcher(input);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement(rewrite.apply(matcher)));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private interface Rewrite {
        String apply(Matcher match);
    }
}
