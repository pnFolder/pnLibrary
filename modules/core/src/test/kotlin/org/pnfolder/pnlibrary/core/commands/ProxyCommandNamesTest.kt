package org.pnfolder.pnlibrary.core.commands

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProxyCommandNamesTest {
    @Test
    fun `text and click commands of the shared output name the proxy command`() {
        val message = Component.text("Откройте /pn servers или /pnlib")
            .append(Component.text("[Проверить]").clickEvent(ClickEvent.runCommand("/pn groups grief check")))

        val shown = ProxyCommandNames.rewrite(message)

        assertEquals("Откройте /pnproxy servers или /pnlib[Проверить]", PlainTextComponentSerializer.plainText().serialize(shown))
        assertEquals("/pnproxy groups grief check", (shown.children().last().clickEvent() as ClickEvent).value())
    }

    @Test
    fun `the proxy owns only its own names`() {
        assertTrue(ProxyCommandNames.owns("pnproxy"))
        assertTrue(ProxyCommandNames.owns("PNP"))
        assertFalse(ProxyCommandNames.owns("pn"))
    }
}
