package org.pnfolder.pnlibrary.core.network.backend;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.network.message.MessageCodec;
import org.pnfolder.pnlibrary.api.network.channel.ServerChannel;
import org.pnfolder.pnlibrary.api.network.channel.typed.ServerChannelOf;

/** A game-server channel seen as objects of one class. */
final class CodecBackendChannel<T> implements ServerChannelOf<T> {
    private final ServerChannel channel;
    private final MessageCodec<T> codec;
    private final ConcurrentMap<Consumer<T>, Consumer<byte[]>> raw = new ConcurrentHashMap<>();

    CodecBackendChannel(ServerChannel channel, MessageCodec<T> codec) {
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

    @NotNull
    @Override
    public CompletionStage<Boolean> send(@NotNull T message) {
        return channel.send(codec.toBytes(message));
    }

    @Override
    public void listen(@NotNull Consumer<T> listener) {
        Consumer<byte[]> decoder = data -> listener.accept(codec.fromBytes(data));
        if (raw.putIfAbsent(listener, decoder) == null) {
            channel.listen(decoder);
        }
    }

    @Override
    public void unlisten(@NotNull Consumer<T> listener) {
        Consumer<byte[]> decoder = raw.remove(listener);
        if (decoder != null) {
            channel.unlisten(decoder);
        }
    }
}
