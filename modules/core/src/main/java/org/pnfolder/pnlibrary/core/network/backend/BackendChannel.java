package org.pnfolder.pnlibrary.core.network.backend;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.network.channel.ServerChannel;
import org.pnfolder.pnlibrary.api.network.channel.typed.ServerChannelOf;
import org.pnfolder.pnlibrary.api.network.message.MessageCodec;
import org.pnfolder.pnlibrary.core.network.ClosableChannel;
import org.pnfolder.pnlibrary.core.network.ListenerSet;
import org.pnfolder.pnlibrary.core.network.MessageSize;

/** One open plugin-message channel of a game server; messages wait here while no player can carry them. */
public final class BackendChannel implements ServerChannel, ClosableChannel {
    static final int QUEUE_LIMIT = 100;

    private final Key key;
    private final String name; // what the platform and the messages call the channel
    private final Runnable release;
    private final BackendChannels.Outgoing outgoing;
    private final ListenerSet<Consumer<byte[]>> listeners;
    private final Queue<Pending> waiting = new ArrayDeque<>();
    private volatile boolean open = true;

    BackendChannel(Key key, Runnable release, BackendChannels.Outgoing outgoing,
                   BiConsumer<String, Throwable> failures) {
        this.key = key;
        this.name = key.asString();
        this.release = release;
        this.outgoing = outgoing;
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
    public void listen(@NotNull Consumer<byte[]> listener) {
        listeners.add(listener);
    }

    @Override
    public void unlisten(@NotNull Consumer<byte[]> listener) {
        listeners.remove(listener);
    }

    @NotNull
    @Override
    public CompletionStage<Boolean> send(@NotNull byte[] data) {
        if (!open) {
            throw new IllegalStateException("Channel " + name + " is closed");
        }
        MessageSize.check(name, data);
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        synchronized (waiting) {
            if (waiting.isEmpty() && outgoing.send(name, data)) {
                result.complete(true);
                return result;
            }
            if (waiting.size() >= QUEUE_LIMIT) {
                waiting.remove().result.complete(false);
            }
            waiting.add(new Pending(data, result));
        }
        return result;
    }

    @NotNull
    @Override
    public <T> ServerChannelOf<T> with(@NotNull MessageCodec<T> codec) {
        return new CodecBackendChannel<>(this, codec);
    }

    @Override
    public void close() {
        release.run();
    }

    @Override
    public void markClosed() {
        open = false;
        listeners.clear();
        synchronized (waiting) {
            for (Pending pending : waiting) {
                pending.result.complete(false);
            }
            waiting.clear();
        }
    }

    /** Sends the waiting messages in order for as long as a player can carry them. */
    void flush() {
        synchronized (waiting) {
            while (!waiting.isEmpty() && outgoing.send(name, waiting.peek().data)) {
                waiting.remove().result.complete(true);
            }
        }
    }

    void deliver(byte[] data) {
        listeners.deliver(listener -> listener.accept(data));
    }

    private static final class Pending {
        private final byte[] data;
        private final CompletableFuture<Boolean> result;

        Pending(byte[] data, CompletableFuture<Boolean> result) {
            this.data = data;
            this.result = result;
        }
    }
}
