package org.pnfolder.pnlibrary.core.network;

import net.kyori.adventure.key.Key;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.network.NetworkServer;
import org.pnfolder.pnlibrary.api.network.channel.MessageChannel;
import org.pnfolder.pnlibrary.api.network.channel.NetworkChannel;
import org.pnfolder.pnlibrary.api.network.channel.ServerChannel;
import org.pnfolder.pnlibrary.api.network.channel.typed.NetworkChannelOf;
import org.pnfolder.pnlibrary.api.network.channel.typed.ServerChannelOf;
import org.pnfolder.pnlibrary.api.network.message.MessageCodec;
import org.pnfolder.pnlibrary.api.network.message.MessageReader;
import org.pnfolder.pnlibrary.api.network.message.MessageWriter;
import org.pnfolder.pnlibrary.core.network.backend.BackendChannels;
import org.pnfolder.pnlibrary.core.network.proxy.ProxyChannels;

class TypedMessagesTest {
    private final NetworkServer lobby = mock(NetworkServer.class);
    private final List<byte[]> toProxy = new ArrayList<>();
    private final BackendChannels backend = new BackendChannels(n -> { }, n -> { }, (name, data) -> toProxy.add(data), (m, e) -> { });
    private final ProxyChannels proxy = new ProxyChannels(n -> { }, n -> { }, () -> Collections.singletonList(lobby), (m, e) -> { });

    @Test
    void aTypedMessageTravelsFromServerToProxy() {
        UUID id = UUID.randomUUID();
        AtomicReference<String> seen = new AtomicReference<>();
        NetworkChannel channel = proxy.channel(Key.key("my:main"));
        channel.on("balance", (from, msg) -> seen.set(msg.readUuid() + ":" + msg.readInt()));
        channel.on("other", (from, msg) -> seen.set("wrong"));

        ServerChannel server = backend.channel(Key.key("my:main"));
        server.send("balance", msg -> msg.writeUuid(id).writeInt(250));
        proxy.dispatch("my:main", lobby, toProxy.get(0));

        assertEquals(id + ":250", seen.get());
    }

    @Test
    void theHandleFromOnStopsThatListenerOnly() {
        AtomicReference<String> seen = new AtomicReference<>("");
        NetworkChannel channel = proxy.channel(Key.key("my:main"));
        Runnable stop = channel.on("a", (from, msg) -> seen.set(seen.get() + "a"));
        channel.on("a", (from, msg) -> seen.set(seen.get() + "b"));
        ServerChannel server = backend.channel(Key.key("my:main"));

        server.send("a", msg -> { });
        proxy.dispatch("my:main", lobby, toProxy.get(0));
        stop.run();
        server.send("a", msg -> { });
        proxy.dispatch("my:main", lobby, toProxy.get(1));

        assertEquals("abb", seen.get());
    }

    @Test
    void aTypedMessageTravelsFromProxyToServer() {
        List<byte[]> toServer = new ArrayList<>();
        org.mockito.Mockito.when(lobby.send(org.mockito.ArgumentMatchers.eq("my:main"), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(call -> toServer.add(call.getArgument(1)));
        AtomicReference<String> seen = new AtomicReference<>();
        ServerChannel server = backend.channel(Key.key("my:main"));
        server.on("hello", msg -> seen.set(msg.readText() + msg.readBoolean() + new String(msg.readBlock())));

        proxy.channel(Key.key("my:main")).send(lobby, "hello", msg -> msg.writeText("hi").writeBoolean(true).writeBlock("ok".getBytes()));
        backend.dispatch("my:main", toServer.get(0));

        assertEquals("hitrueok", seen.get());
    }

    @Test
    void rawBytesAndForeignTypesAreIgnoredAndShortMessagesFailTheListenerOnly() {
        AtomicReference<String> seen = new AtomicReference<>();
        ServerChannel server = backend.channel(Key.key("my:main"));
        server.on("n", msg -> seen.set(String.valueOf(msg.readInt())));
        backend.dispatch("my:main", new byte[] {0});                  // not a typed message
        assertNull(seen.get());
        server.send("n", msg -> { });                                  // an int is missing
        backend.dispatch("my:main", toProxy.get(0));                  // logged, does not throw
        assertNull(seen.get());
    }

    static final class Balance {
        final UUID id;
        final int amount;

        Balance(UUID id, int amount) {
            this.id = id;
            this.amount = amount;
        }
    }

    /** One codec, used by both sides. */
    static final class BalanceCodec implements org.pnfolder.pnlibrary.api.network.message.MessageCodec<Balance> {
        @Override
        public void encode(Balance message, org.pnfolder.pnlibrary.api.network.message.MessageWriter out) {
            out.writeUuid(message.id).writeInt(message.amount);
        }

        @Override
        public Balance decode(org.pnfolder.pnlibrary.api.network.message.MessageReader in) {
            return new Balance(in.readUuid(), in.readInt());
        }
    }

    @Test
    void aCodecCarriesAClassInBothDirections() {
        List<byte[]> toServer = new ArrayList<>();
        org.mockito.Mockito.when(lobby.send(org.mockito.ArgumentMatchers.eq("my:main"), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(call -> toServer.add(call.getArgument(1)));
        UUID id = UUID.randomUUID();
        AtomicReference<Balance> onProxy = new AtomicReference<>();
        AtomicReference<Balance> onServer = new AtomicReference<>();
        NetworkChannelOf<Balance> proxyTyped =
                proxy.channel(Key.key("my:main")).with(new BalanceCodec());
        ServerChannelOf<Balance> serverTyped =
                backend.channel(Key.key("my:main")).with(new BalanceCodec());
        proxyTyped.listen((from, msg) -> onProxy.set(msg));
        serverTyped.listen(onServer::set);

        serverTyped.send(new Balance(id, 7));
        proxy.dispatch("my:main", lobby, toProxy.get(0));
        proxyTyped.send(lobby, new Balance(id, 9));
        backend.dispatch("my:main", toServer.get(0));

        assertEquals(7, onProxy.get().amount);
        assertEquals(id, onProxy.get().id);
        assertEquals(9, onServer.get().amount);
    }

    @Test
    void unlistenStopsAndATooLongMessageIsRefused() {
        AtomicReference<Balance> seen = new AtomicReference<>();
        ServerChannelOf<Balance> typed =
                backend.channel(Key.key("my:main")).with(new BalanceCodec());
        java.util.function.Consumer<Balance> listener = seen::set;
        typed.listen(listener);
        typed.unlisten(listener);
        typed.send(new Balance(UUID.randomUUID(), 1));
        backend.dispatch("my:main", toProxy.get(0));
        assertNull(seen.get());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> backend.channel(Key.key("my:main")).send(new byte[org.pnfolder.pnlibrary.api.network.channel.MessageChannel.MAX_MESSAGE_BYTES + 1]));
    }
}
