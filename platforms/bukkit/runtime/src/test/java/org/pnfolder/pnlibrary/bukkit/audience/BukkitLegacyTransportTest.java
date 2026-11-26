package org.pnfolder.pnlibrary.bukkit.audience;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import java.util.logging.Logger;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.pnfolder.pnlibrary.bukkit.PnLoggerBuffer;

/** A 1.8 server has no native Adventure and may lack adventure-platform-bukkit: everything must still reach the player as plain legacy text. */
class BukkitLegacyTransportTest {
    private final Server server = mock(Server.class);
    private final Player player = mock(Player.class);
    private final Plugin plugin = mock(Plugin.class);
    private final BukkitAdventureTransport transport;

    BukkitLegacyTransportTest() {
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        when(plugin.getServer()).thenReturn(server);
        transport = new BukkitAdventureTransport(plugin, new PnLoggerBuffer(Logger.getAnonymousLogger(), () -> null), null);
    }

    @Test
    void aMessageBecomesSectionCodedTextWithoutItsClickEvent() {
        Component message = Component.text("Open", NamedTextColor.GREEN).clickEvent(ClickEvent.runCommand("/pn"));

        transport.sendMessage(player, message);

        verify(player).sendMessage("§aOpen");
    }

    @Test
    void aHexColorIsReducedToTheNearestOfTheSixteenLegacyColors() {
        transport.sendMessage(player, Component.text("x", TextColor.color(0xFF0000)));

        ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        verify(player).sendMessage(sent.capture());
        assertTrue(sent.getValue().matches("§[0-9a-f]x"), sent.getValue());
    }

    @Test
    void aTitleGoesThroughTheTwoLineLegacyCall() {
        transport.showTitle(player, Title.title(Component.text("Hi", NamedTextColor.RED), Component.text("there")));

        verify(player).sendTitle("§cHi", "there");
    }

    @Test
    void withoutAdventureTheTransportReportsItselfAsLegacyOnly() {
        assertEquals(false, transport.isNativeTransportAvailable());
    }
}
