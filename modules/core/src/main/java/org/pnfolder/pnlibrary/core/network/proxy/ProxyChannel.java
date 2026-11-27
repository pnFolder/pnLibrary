package org.pnfolder.pnlibrary.core.network.proxy;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.network.NetworkServer;
import org.pnfolder.pnlibrary.api.network.channel.typed.NetworkChannelOf;
import org.pnfolder.pnlibrary.api.network.channel.NetworkChannel;
import org.pnfolder.pnlibrary.api.network.channel.typed.NetworkChannelOf;
import org.pnfolder.pnlibrary.api.network.message.MessageCodec;
import org.pnfolder.pnlibrary.core.network.ClosableChannel;
import org.pnfolder.pnlibrary.core.network.ListenerSet;
import org.pnfolder.pnlibrary.core.network.MessageSize;

/** One open plugin-message channel of a proxy: its listeners and the way out to the servers. */
public final class ProxyChannel implements NetworkChannel, ClosableChannel {
    private final Key key;
    private final String name; // what the platform and the messages call the channel
    private final Runnable release;
    private final Supplier<List<NetworkServer>> servers;
    private final ListenerSet<NetworkChannelOf.Listener<byte[]>> listeners;
    private volatile boolean open = true;

    ProxyChannel(Key key, Runnable release, Supplier<List<NetworkServer>> servers,
                 BiConsumer<String, Throwable> failures) {
        this.key = key;
        this.name = key.asString();
        this.release = release;
        this.servers = servers;
        this.listeners = new ListenerSet<>(key.asString(), failures);
    }

    @NotNull
    @Override
    public Key key() {
        return key;
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public void listen(@NotNull NetworkChannelOf.Listener<byte[]> listener) {
        listeners.add(listener);
    }

    @Override
    public void unlisten(@NotNull NetworkChannelOf.Listener<byte[]> listener) {
        listeners.remove(listener);
    }

    @Override
    public boolean send(@NotNull NetworkServer server, @NotNull byte[] data) {
        requireOpen();
        MessageSize.check(name, data);
        return server.send(name, data);
    }

    @Override
    public int broadcast(@NotNull byte[] data) {
        requireOpen();
        MessageSize.check(name, data);
        int delivered = 0;
        for (NetworkServer server : servers.get()) {
            if (server.send(name, data)) {
                delivered++;
            }
        }
        return delivered;
    }

    @NotNull
    @Override
    public <T> NetworkChannelOf<T> with(@NotNull MessageCodec<T> codec) {
        return new CodecProxyChannel<>(this, codec);
    }

    @Override
    public void close() {
        release.run();
    }

    @Override
    public void markClosed() {
        open = false;
        listeners.clear();
    }

    void deliver(NetworkServer from, byte[] data) {
        listeners.deliver(listener -> listener.onMessage(from, data));
    }

    private void requireOpen() {
        if (!open) {
            throw new IllegalStateException("Channel " + name + " is closed");
        }
    }
}
