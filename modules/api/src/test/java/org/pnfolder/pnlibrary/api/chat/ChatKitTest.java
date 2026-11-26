package org.pnfolder.pnlibrary.api.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.chat.notice.ChatNotice;
import org.pnfolder.pnlibrary.api.chat.notice.ChatTone;

class ChatKitTest {
    private final ChatKit player = new ChatKit(ChatReader.PLAYER);
    private final ChatKit console = new ChatKit(ChatReader.CONSOLE);

    private static String plain(Component component) {
        StringBuilder text = new StringBuilder();
        if (component instanceof TextComponent) {
            text.append(((TextComponent) component).content());
        }
        for (Component child : component.children()) {
            text.append(plain(child));
        }
        return text.toString();
    }

    private static String repeat(char symbol, int count) {
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < count; index++) {
            text.append(symbol);
        }
        return text.toString();
    }

    @Test
    @DisplayName("only https links without credentials are safe")
    void onlyHttpsLinksWithoutCredentialsAreSafe() {
        assertTrue(player.safeLink("https://example.org/page"));
        assertFalse(player.safeLink("http://example.org/page"));
        assertFalse(player.safeLink("https://user:pass@example.org/"));
        assertFalse(player.safeLink("javascript:alert(1)"));
        assertFalse(player.safeLink("https://"));
        assertFalse(player.safeLink("not a link"));
        assertFalse(player.safeLink("https://example.org/" + repeat('a', 5000)));
    }

    @Test
    @DisplayName("a link button is left out for an unsafe link or a reader that cannot click")
    void aLinkButtonIsLeftOutForAnUnsafeLinkOrAReaderThatCannotClick() {
        assertEquals(Component.empty(), player.linkButton("open", "http://example.org", "hint", ChatPalette.ACCENT));
        assertEquals(Component.empty(), console.linkButton("open", "https://example.org", "hint", ChatPalette.ACCENT));
        assertNotNull(player.linkButton("open", "https://example.org", "hint", ChatPalette.ACCENT).clickEvent());
    }

    @Test
    @DisplayName("a command button runs its command and a console gets none")
    void aCommandButtonRunsItsCommandAndAConsoleGetsNone() {
        ClickEvent click = player.button("[Go]", "/pn go", "hint", ChatPalette.ACCENT).clickEvent();

        assertEquals(ClickEvent.Action.RUN_COMMAND, click.action());
        assertEquals("[Go]", plain(player.button("[Go]", "/pn go", "hint", ChatPalette.ACCENT)));
        assertEquals(Component.empty(), console.button("Go", "/pn go", "hint", ChatPalette.ACCENT));
    }

    @Test
    @DisplayName("the console gets ascii stand-ins and keeps cyrillic")
    void theConsoleGetsAsciiStandInsAndKeepsCyrillic() {
        assertEquals("a -> b - ok...", console.shown("a → b — ok…"));
        assertEquals("Привет ёЁ", console.shown("Привет ёЁ"));
        assertEquals("a?b", console.shown("a☃b"));
        assertEquals("a → b", player.shown("a → b"));
    }

    @Test
    @DisplayName("a notice shows the mark, the text and a detail line")
    void aNoticeShowsTheMarkTheTextAndADetailLine() {
        List<Component> lines = player.notice(new ChatNotice(ChatTone.FAILURE, "Не вышло", "причина"));

        assertEquals(2, lines.size());
        assertTrue(plain(lines.get(0)).endsWith("Не вышло"));
        assertEquals("    причина", plain(lines.get(1)));
        assertEquals(1, player.notice(new ChatNotice(ChatTone.INFO, "Готово", null)).size());
    }

    @Test
    @DisplayName("an overlong message is cut")
    void anOverlongMessageIsCut() {
        List<Component> lines = player.notice(new ChatNotice(ChatTone.INFO, repeat('x', 2000), null));

        assertEquals(1, lines.size());
        assertTrue(plain(lines.get(0)).length() < 600);
    }

    @Test
    @DisplayName("a confirmation tells a reader who cannot click which command to type")
    void aConfirmationTellsAReaderWhoCannotClickWhichCommandToType() {
        List<Component> page = console.confirmation("Удалить", Collections.singletonList("Это нельзя отменить."),
            "Да", "/pn delete 1", "hint");
        StringBuilder text = new StringBuilder();
        for (Component line : page) {
            text.append(plain(line)).append('\n');
            assertNull(line.clickEvent());
        }

        assertTrue(text.toString().contains("/pn delete 1"));
    }

    @Test
    @DisplayName("command hints make the command clickable only for readers who can click")
    void commandHintsMakeTheCommandClickableOnlyForReadersWhoCanClick() {
        List<Component> hints = player.commandHints(new CommandHint("/pn give <player> [amount]", "выдать"));
        Component hint = hints.get(hints.size() - 1);

        assertEquals(ClickEvent.Action.SUGGEST_COMMAND, hint.clickEvent().action());
        assertEquals("/pn give ", hint.clickEvent().value());
        List<Component> plainHints = console.commandHints(new CommandHint("/pn give <player>", "выдать"));
        assertNull(plainHints.get(plainHints.size() - 1).clickEvent());
    }

    @Test
    @DisplayName("a page starts with an empty line")
    void aPageStartsWithAnEmptyLine() {
        assertEquals(Component.empty(), player.framed(Collections.singletonList(player.header("T"))).get(0));
    }
}
