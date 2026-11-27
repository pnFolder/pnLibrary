package org.pnfolder.pnlibrary.core.network.proxy;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.network.channel.typed.NetworkChannelOf;
import org.pnfolder.pnlibrary.api.network.message.MessageCodec;
import org.pnfolder.pnlibrary.api.network.channel.NetworkChannel;
import org.pnfolder.pnlibrary.api.network.NetworkServer;
import org.pnfolder.pnlibrary.api.network.channel.typed.NetworkChannelOf;

/** A proxy channel seen as objects of one class. */
final class CodecProxyChannel<T> implements NetworkChannelOf<T> {
    private final NetworkChannel channel;
    private final MessageCodec<T> codec;
    private final ConcurrentMap<Listener<T>, NetworkChannelOf.Listener<byte[]>> raw = new ConcurrentHashMap<>();

    CodecProxyChannel(NetworkChannel channel, MessageCodec<T> codec) {
        this.channel = channel;
        this.codec = codec;
    }

    @NotNull
    @Override
    public Key key() {
        return channel.key();
    }

    @Override
    public boolean isOpen() {
        return channel.isOpen();
    }

    @Override
    public void close() {
        channel.close();
    }

    @Override
    public boolean send(@NotNull NetworkServer server, @NotNull T message) {
        return channel.send(server, codec.toBytes(message));
    }

    @Override
    public int broadcast(@NotNull T message) {
        return channel.broadcast(codec.toBytes(message));
    }

    @Override
    public void listen(@NotNull Listener<T> listener) {
        NetworkChannelOf.Listener<byte[]> decoder = (from, data) -> listener.onMessage(from, codec.fromBytes(data));
        if (raw.putIfAbsent(listener, decoder) == null) {
            channel.listen(decoder);
        }
    }

    @Override
    public void unlisten(@NotNull Listener<T> listener) {
        NetworkChannelOf.Listener<byte[]> decoder = raw.remove(listener);
        if (decoder != null) {
            channel.unlisten(decoder);
        }
    }
}
