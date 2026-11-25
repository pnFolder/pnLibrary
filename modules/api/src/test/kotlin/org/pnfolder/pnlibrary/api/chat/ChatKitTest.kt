package org.pnfolder.pnlibrary.api.chat

import org.pnfolder.pnlibrary.api.chat.notice.ChatTone
import org.pnfolder.pnlibrary.api.chat.notice.ChatNotice
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.event.ClickEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChatKitTest {
    private val player = ChatKit(ChatReader.PLAYER)
    private val console = ChatKit(ChatReader.CONSOLE)

    private fun plain(component: Component): String =
        ((component as? TextComponent)?.content()?.toString().orEmpty()) + component.children().joinToString("") { plain(it) }

    @Test
    fun `only https links without credentials are safe`() {
        assertTrue(player.safeLink("https://example.org/page"))
        assertFalse(player.safeLink("http://example.org/page"))
        assertFalse(player.safeLink("https://user:pass@example.org/"))
        assertFalse(player.safeLink("javascript:alert(1)"))
        assertFalse(player.safeLink("https://"))
        assertFalse(player.safeLink("not a link"))
        assertFalse(player.safeLink("https://example.org/" + "a".repeat(5000)))
    }

    @Test
    fun `a link button is left out for an unsafe link or a reader that cannot click`() {
        assertEquals(Component.empty(), player.linkButton("open", "http://example.org", "hint", ChatPalette.ACCENT))
        assertEquals(Component.empty(), console.linkButton("open", "https://example.org", "hint", ChatPalette.ACCENT))
        assertNotNull(player.linkButton("open", "https://example.org", "hint", ChatPalette.ACCENT).clickEvent())
    }

    @Test
    fun `a command button runs its command and a console gets none`() {
        val click = player.button("[Go]", "/pn go", "hint", ChatPalette.ACCENT).clickEvent()

        assertEquals(ClickEvent.Action.RUN_COMMAND, click?.action())
        assertEquals("[Go]", plain(player.button("[Go]", "/pn go", "hint", ChatPalette.ACCENT)))
        assertEquals(Component.empty(), console.button("Go", "/pn go", "hint", ChatPalette.ACCENT))
    }

    @Test
    fun `the console gets ascii stand-ins and keeps cyrillic`() {
        assertEquals("a -> b - ok...", console.shown("a → b — ok…"))
        assertEquals("Привет ёЁ", console.shown("Привет ёЁ"))
        assertEquals("a?b", console.shown("a☃b"))
        assertEquals("a → b", player.shown("a → b"))
    }

    @Test
    fun `a notice shows the mark, the text and a detail line`() {
        val lines = player.notice(ChatNotice(ChatTone.FAILURE, "Не вышло", "причина"))

        assertEquals(2, lines.size)
        assertTrue(plain(lines[0]).endsWith("Не вышло"))
        assertEquals("    причина", plain(lines[1]))
        assertEquals(1, player.notice(ChatNotice(ChatTone.INFO, "Готово", null)).size)
    }

    @Test
    fun `an overlong message is cut`() {
        val line = plain(player.notice(ChatNotice(ChatTone.INFO, "x".repeat(2000), null)).single())

        assertTrue(line.length < 600)
    }

    @Test
    fun `a confirmation tells a reader who cannot click which command to type`() {
        val page = console.confirmation("Удалить", listOf("Это нельзя отменить."), "Да", "/pn delete 1", "hint")
        val text = page.joinToString("\n") { plain(it) }

        assertTrue(text.contains("/pn delete 1"))
        assertTrue(page.all { it.clickEvent() == null })
    }

    @Test
    fun `command hints make the command clickable only for readers who can click`() {
        val hint = player.commandHints("/pn give <player> [amount]" to "выдать").last()

        assertEquals(ClickEvent.Action.SUGGEST_COMMAND, hint.clickEvent()?.action())
        assertEquals("/pn give ", hint.clickEvent()?.value())
        assertNull(console.commandHints("/pn give <player>" to "выдать").last().clickEvent())
    }

    @Test
    fun `a page starts with an empty line`() {
        assertEquals(Component.empty(), player.framed(listOf(player.header("T"))).first())
    }
}
