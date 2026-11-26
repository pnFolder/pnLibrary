package org.pnfolder.pnlibrary.bungee.network;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.audience.AudienceExtensions;
import org.pnfolder.pnlibrary.api.audience.OnlinePlayer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Checks how proxy senders and players are converted to portable audiences. */
class BungeeAudienceAdapterTest {
    private final ProxyServer proxy = mock(ProxyServer.class);
    private final Plugin plugin = mock(Plugin.class);
    private final List<String> sent = new ArrayList<>();
    // Varargs are not matched by argument matchers, so the console records what it is sent by method name.
    private final CommandSender console = mock(CommandSender.class, invocation -> {
        switch (invocation.getMethod().getName()) {
            case "getName":
                return "CONSOLE";
            case "sendMessage":
                for (Object argument : invocation.getArguments()) {
                    Object[] items = argument instanceof Object[] ? (Object[]) argument : new Object[] {argument};
                    for (Object item : items) {
                        if (item instanceof BaseComponent) {
                            sent.add(((BaseComponent) item).toLegacyText());
                        }
                    }
                }
                return null;
            default:
                return null;
        }
    });
    private final BungeeAudienceAdapter adapter;

    BungeeAudienceAdapterTest() {
        when(plugin.getProxy()).thenReturn(proxy);
        when(proxy.getConsole()).thenReturn(console);
        adapter = new BungeeAudienceAdapter(plugin);
    }

    private ProxiedPlayer player(String name) {
        ProxiedPlayer player = mock(ProxiedPlayer.class);
        when(player.getName()).thenReturn(name);
        when(player.getUniqueId()).thenReturn(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)));
        return player;
    }

    @Test
    void theConsoleIsAConsoleAudience() {
        Audience sender = adapter.console();

        assertTrue(AudienceExtensions.isConsole(sender));
        assertEquals("CONSOLE", AudienceExtensions.name(sender));
    }

    @Test
    void otherSendersAreWrappedAndUnknownObjectsAreNot() {
        CommandSender other = mock(CommandSender.class);
        when(other.getName()).thenReturn("rcon");

        assertFalse(AudienceExtensions.isPlayer(adapter.of(other)));
        assertNotNull(adapter.of(player("Alex")));
        assertNull(adapter.of("not a sender"));
    }

    @Test
    void onlinePlayersAreFoundByIdAndListed() {
        ProxiedPlayer alex = player("Alex");
        when(proxy.getPlayer(alex.getUniqueId())).thenReturn(alex);
        when(proxy.getPlayers()).thenReturn(Collections.singletonList(alex));

        assertEquals("Alex", AudienceExtensions.name(adapter.player(alex.getUniqueId())));
        assertNull(adapter.player(UUID.randomUUID()));
        List<String> names = new ArrayList<>();
        for (OnlinePlayer online : adapter.players()) {
            names.add(AudienceExtensions.name(online));
        }
        assertEquals(Collections.singletonList("Alex"), names);
    }

    @Test
    void aConsoleActionBarFallsBackToChatAndSoundsAreUnsupported() {
        Audience sender = adapter.console();

        sender.sendActionBar(Component.text("hello"));

        List<String> plain = new ArrayList<>();
        for (String line : sent) {
            plain.add(ChatColor.stripColor(line));
        }
        assertEquals(Collections.singletonList("hello"), plain);
        sender.playSound(mock(Sound.class));
    }

    @Test
    void aPlayerCanReadMessagesAndTheActionBarButNothingABackendMustRender() {
        OnlinePlayer library = BungeeNetworkPlayer.of(player("Alex"));

        assertNull(library.position());
        assertNull(library.experienceLevel());
        library.playSound(mock(Sound.class));
    }
}
