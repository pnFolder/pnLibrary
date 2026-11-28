package org.pnfolder.pnlibrary.core.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.network.NetworkServer;

class AddedServersTest {
    private final List<String> proxy = new ArrayList<>(Arrays.asList("lobby"));
    private final AddedServers added = new AddedServers(new AddedServers.Platform() {
        @Override
        public boolean exists(String name) {
            return proxy.stream().anyMatch(known -> known.equalsIgnoreCase(name));
        }

        @Override
        public NetworkServer register(String name, InetSocketAddress address) {
            proxy.add(name);
            return named(name);
        }

        @Override
        public void unregister(String name) {
            proxy.remove(name);
        }
    });
    private final InetSocketAddress address = new InetSocketAddress("127.0.0.1", 25570);

    private static NetworkServer named(String name) {
        NetworkServer server = mock(NetworkServer.class);
        when(server.getName()).thenReturn(name);
        return server;
    }

    @Test
    void addsAServerAndRemovesIt() {
        NetworkServer arena = added.add("arena-12", address);

        assertEquals("arena-12", arena.getName());
        assertTrue(proxy.contains("arena-12"));
        assertEquals(Arrays.asList("arena-12"), added.names());
        assertTrue(added.remove(arena));
        assertFalse(proxy.contains("arena-12"));
        assertFalse(added.remove(arena), "the second removal finds nothing");
    }

    @Test
    void refusesABadNameATakenNameAndAnUnresolvedAddress() {
        assertThrows(IllegalArgumentException.class, () -> added.add("arena 12", address));
        assertThrows(IllegalArgumentException.class, () -> added.add("", address));
        assertThrows(IllegalArgumentException.class, () -> added.add("Lobby", address), "the same name in another case is taken");
        assertThrows(IllegalArgumentException.class, () -> added.add("far", InetSocketAddress.createUnresolved("nowhere.invalid", 1)));
        added.add("arena", address);
        assertThrows(IllegalArgumentException.class, () -> added.add("ARENA", address));
    }

    @Test
    void anyServerCanBeRemovedAlsoTheProxysOwn() {
        assertTrue(added.remove(named("lobby")));
        assertFalse(proxy.contains("lobby"));
        assertFalse(added.remove(named("lobby")), "the second removal finds nothing");
    }

    @Test
    void aRemovalMatchesTheNameInAnyCase() {
        added.add("Arena", address);
        assertTrue(added.remove(named("arena".toUpperCase(Locale.ROOT))));
    }

    @Test
    void closeRemovesEveryAddedServerAndNothingElse() {
        added.add("a", address);
        added.add("b", address);
        added.close();

        assertEquals(Arrays.asList("lobby"), proxy);
        assertTrue(added.names().isEmpty());
    }

    @Test
    void anAddressIsReadFromHostAndPort() {
        assertEquals(25570, ServerAddress.parse("127.0.0.1:25570").getPort());
        assertEquals(25565, ServerAddress.parse("[::1]:25565").getPort());
        for (String bad : new String[] {"127.0.0.1", ":25565", "127.0.0.1:", "127.0.0.1:abc", "127.0.0.1:0", "127.0.0.1:70000",
                "no-such-host.invalid:25565"}) {
            assertThrows(IllegalArgumentException.class, () -> ServerAddress.parse(bad), bad);
        }
    }
}
