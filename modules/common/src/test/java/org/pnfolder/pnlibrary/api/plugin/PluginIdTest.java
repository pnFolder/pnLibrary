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
        for (String value : Arrays.asList("", "-plugin", "plugin name", "plugin/one", repeat("a", 65))) {
            assertThrows(IllegalArgumentException.class, () -> PluginId.of(value));
        }
    }

    private static String repeat(String text, int count) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < count; i++) {
            out.append(text);
        }
        return out.toString();
    }
}
