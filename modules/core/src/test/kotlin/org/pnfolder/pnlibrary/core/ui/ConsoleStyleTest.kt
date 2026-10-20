package org.pnfolder.pnlibrary.core.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.chat.ChatPalette
import org.pnfolder.pnlibrary.api.chat.ChatTone

class ConsoleStyleTest {
    @Test
    fun `palette roles land on their documented legacy colors`() {
        assertEquals("§f", ConsoleStyle.color(ChatPalette.TEXT))
        assertEquals("§7", ConsoleStyle.color(ChatPalette.SOFT))
        assertEquals("§8", ConsoleStyle.color(ChatPalette.FAINT))
        assertEquals("§d", ConsoleStyle.color(ChatPalette.ACCENT))
        assertEquals("§d", ConsoleStyle.color(ChatPalette.LABEL))
        assertEquals("§a", ConsoleStyle.color(ChatPalette.OK))
        assertEquals("§b", ConsoleStyle.color(ChatPalette.INFO))
        assertEquals("§e", ConsoleStyle.color(ChatPalette.WARN))
        assertEquals("§6", ConsoleStyle.color(ChatPalette.ORANGE))
        assertEquals("§c", ConsoleStyle.color(ChatPalette.DANGER))
    }

    @Test
    fun `formats and paint use plain legacy sequences`() {
        assertEquals("§l", ConsoleStyle.BOLD)
        assertEquals("§n", ConsoleStyle.UNDERLINE)
        assertEquals("§r", ConsoleStyle.RESET)
        assertEquals("§aready§r", ConsoleStyle.paint("ready", ChatPalette.OK))
    }

    @Test
    fun `the card theme follows the chat roles and tones only recolor values`() {
        val theme = ConsoleStyle.THEME
        assertEquals(listOf("§d", "§f", "§d", "§7", "§r"), listOf(theme.border, theme.accent, theme.text, theme.muted, theme.reset))

        val failure = ConsoleStyle.theme(ChatTone.FAILURE)
        assertEquals("§c", failure.accent)
        assertEquals(theme.border, failure.border)
        assertEquals(theme.text, failure.text)
    }
}
