package ru.privatenull.pnlibrary.text;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ColorUtilTest {
    @Test
    void supportsLegacyAndMiniMessageTogether() {
        assertEquals("§aHello §lworld", ColorUtil.colorize("&aHello <bold>world</bold>"));
    }

    @Test
    void supportsAmpersandHex() {
        assertEquals("§x§d§8§d§f§9§dText", ColorUtil.colorize("&#D8DF9DText"));
    }

    @Test
    void legacyColorClearsBoldFormatting() {
        assertEquals("§lPrefix §fNormal", ColorUtil.colorize("§lPrefix &fNormal"));
    }

    @Test
    void hexColorClearsBoldFormatting() {
        assertEquals("§lPrefix §x§d§8§d§f§9§dNormal", ColorUtil.colorize("§lPrefix &#D8DF9DNormal"));
    }
}
