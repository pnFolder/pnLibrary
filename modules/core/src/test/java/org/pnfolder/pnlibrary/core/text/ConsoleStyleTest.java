package org.pnfolder.pnlibrary.core.text;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.chat.style.ChatPalette;
import org.pnfolder.pnlibrary.api.chat.style.ChatTone;
import org.pnfolder.pnlibrary.console.ConsoleTheme;

class ConsoleStyleTest {
    @Test
    void paletteRolesLandOnTheirDocumentedLegacyColors() {
        assertEquals("§f", ConsoleStyle.color(ChatPalette.TEXT));
        assertEquals("§7", ConsoleStyle.color(ChatPalette.SOFT));
        assertEquals("§8", ConsoleStyle.color(ChatPalette.FAINT));
        assertEquals("§d", ConsoleStyle.color(ChatPalette.ACCENT));
        assertEquals("§d", ConsoleStyle.color(ChatPalette.LABEL));
        assertEquals("§a", ConsoleStyle.color(ChatPalette.OK));
        assertEquals("§b", ConsoleStyle.color(ChatPalette.INFO));
        assertEquals("§e", ConsoleStyle.color(ChatPalette.WARN));
        assertEquals("§6", ConsoleStyle.color(ChatPalette.ORANGE));
        assertEquals("§c", ConsoleStyle.color(ChatPalette.DANGER));
    }

    @Test
    void formatsAndPaintUsePlainLegacySequences() {
        assertEquals("§l", ConsoleStyle.BOLD);
        assertEquals("§n", ConsoleStyle.UNDERLINE);
        assertEquals("§r", ConsoleStyle.RESET);
        assertEquals("§aready§r", ConsoleStyle.paint("ready", ChatPalette.OK));
    }

    @Test
    void theCardThemeFollowsTheChatRolesAndTonesOnlyRecolorValues() {
        ConsoleTheme theme = ConsoleStyle.THEME;
        assertEquals(Arrays.asList("§d", "§f", "§f", "§7", "§r"),
            Arrays.asList(theme.border, theme.accent, theme.text, theme.muted, theme.reset));

        ConsoleTheme failure = ConsoleStyle.theme(ChatTone.FAILURE);
        assertEquals("§c", failure.accent);
        assertEquals(theme.border, failure.border);
        assertEquals(theme.text, failure.text);
    }
}
