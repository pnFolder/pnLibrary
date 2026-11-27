package org.pnfolder.pnlibrary.core.network.proxy;

import net.kyori.adventure.key.Key;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.network.NetworkServer;
import org.pnfolder.pnlibrary.api.network.channel.NetworkChannel;
import org.pnfolder.pnlibrary.core.network.ChannelNames;

class ProxyChannelsTest {
    private final List<Key> registered = new ArrayList<>();
    private final List<Key> unregistered = new ArrayList<>();
    private final NetworkServer lobby = mock(NetworkServer.class);
    private final NetworkServer empty = mock(NetworkServer.class);
    private final List<String> failures = new ArrayList<>();
    private final ProxyChannels channels = new ProxyChannels(registered::add, unregistered::add,
            () -> Arrays.asList(lobby, empty), (message, error) -> failures.add(message));

    @Test
    void opensOnceAndLists() {
        NetworkChannel first = channels.channel(Key.key("my:main"));
        assertSame(first, channels.channel(Key.key("my:main")));
        assertEquals(Collections.singletonList(Key.key("my:main")), registered);
        assertEquals(Collections.singletonList(first), channels.channels());
    }

    @Test
    void rejectsTheReservedNamespaceAndTheKeyRejectsBadNames() {
        assertThrows(IllegalArgumentException.class, () -> channels.channel(Key.key("minecraft", "brand")));
        assertThrows(IllegalArgumentException.class, () -> channels.channel(Key.key("main")));   // no namespace means minecraft
        assertThrows(net.kyori.adventure.key.InvalidKeyException.class, () -> Key.key("Main:x"));
    }

    @Test
    void deliversToListenersAndSurvivesAFailingOne() {
        NetworkChannel channel = channels.channel(Key.key("my:main"));
        List<byte[]> got = new ArrayList<>();
        channel.listen((from, data) -> {
            throw new IllegalStateException("boom");
        });
        channel.listen((from, data) -> got.add(data));
        byte[] payload = {1, 2};
        assertTrue(channels.dispatch("my:main", lobby, payload));
        assertEquals(1, got.size());
        assertEquals(Collections.singletonList("A listener of channel my:main failed"), failures);
        assertFalse(channels.dispatch("other:x", lobby, payload));
    }

    @Test
    void sendsAndBroadcastsAndCountsDelivered() {
        byte[] data = {7};
        when(lobby.send("my:main", data)).thenReturn(true);
        when(empty.send("my:main", data)).thenReturn(false);
        NetworkChannel channel = channels.channel(Key.key("my:main"));
        assertTrue(channel.send(lobby, data));
        assertEquals(1, channel.broadcast(data));
    }

    @Test
    void closeUnregistersAndRefusesSending() {
        NetworkChannel channel = channels.channel(Key.key("my:main"));
        channel.close();
        assertFalse(channel.isOpen());
        assertEquals(Collections.singletonList(Key.key("my:main")), unregistered);
        assertTrue(channels.channels().isEmpty());
        assertFalse(channels.owns("my:main"));
        assertThrows(IllegalStateException.class, () -> channel.send(lobby, new byte[0]));
        assertTrue(channels.channel(Key.key("my:main")).isOpen());
    }

    @Test
    void namesListTheOpenChannelsAndAreEmptyWithoutProxy() {
        channels.channel(Key.key("my:one"));
        channels.channel(Key.key("my:two"));
        org.pnfolder.pnlibrary.api.network.NetworkProxy proxy = mock(org.pnfolder.pnlibrary.api.network.NetworkProxy.class);
        when(proxy.channels()).thenReturn(new ArrayList<NetworkChannel>(channels.channels()));
        assertEquals(Arrays.asList("my:one", "my:two"), ChannelNames.of(proxy));
        assertTrue(ChannelNames.of(null).isEmpty());
    }
}
