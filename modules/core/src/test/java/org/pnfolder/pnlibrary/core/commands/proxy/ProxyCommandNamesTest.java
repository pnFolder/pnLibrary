package org.pnfolder.pnlibrary.core.commands.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProxyCommandNamesTest {
    @Test
    @DisplayName("text and click commands of the shared output name the proxy command")
    void textAndClickCommandsOfTheSharedOutputNameTheProxyCommand() {
        Component message = Component.text("Откройте /pn servers или /pnlib")
                .append(Component.text("[Проверить]").clickEvent(ClickEvent.runCommand("/pn groups grief check")));

        Component shown = ProxyCommandNames.rewrite(message);

        assertEquals("Откройте /pnproxy servers или /pnlib[Проверить]", PlainTextComponentSerializer.plainText().serialize(shown));
        List<Component> children = shown.children();
        assertEquals("/pnproxy groups grief check", children.get(children.size() - 1).clickEvent().value());
    }

    @Test
    @DisplayName("the proxy owns only its own names")
    void theProxyOwnsOnlyItsOwnNames() {
        assertTrue(ProxyCommandNames.owns("pnproxy"));
        assertTrue(ProxyCommandNames.owns("PNP"));
        assertFalse(ProxyCommandNames.owns("pn"));
    }
}
