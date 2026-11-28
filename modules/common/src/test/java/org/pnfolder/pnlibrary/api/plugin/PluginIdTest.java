package org.pnfolder.pnlibrary.api.plugin;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PluginIdTest {
    @Test
    void idsAreNormalizedAndCompareByValue() {
        assertEquals(PluginId.of("pnClans"), PluginId.of(" PNCLANS "));
        assertEquals("pnclans", PluginId.of("pnClans").getValue());
    }

    @Test
    void invalidIdsAreRejected() {
        for (String value : Arrays.asList("", "-plugin", "plugin name", "plugin/one", "@plugin", repeat("a", 130))) {
            assertThrows(IllegalArgumentException.class, () -> PluginId.of(value));
        }
    }

    @Test
    void aModuleKeyOfPluginAndModuleIsValid() {
        assertEquals("shop@core", PluginId.of("Shop@core").getValue());
        assertEquals(129, PluginId.of(repeat("a", 64) + "@" + repeat("b", 64)).getValue().length());
    }

    private static String repeat(String text, int count) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < count; i++) {
            out.append(text);
        }
        return out.toString();
    }
}
