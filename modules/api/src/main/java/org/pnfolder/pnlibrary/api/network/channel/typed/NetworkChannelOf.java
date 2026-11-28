package org.pnfolder.pnlibrary.api.network.channel.typed;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.network.NetworkServer;
import org.pnfolder.pnlibrary.api.network.channel.MessageChannel;
import org.pnfolder.pnlibrary.api.network.channel.NetworkChannel;
import org.pnfolder.pnlibrary.api.network.message.MessageCodec;

/**
 * A proxy channel that carries messages of one class {@code D}. {@link NetworkChannel} is the one for {@code byte[]};
 * {@link NetworkChannel#with(MessageCodec)} gives one for your own class.
 *
 * @param <D> what a message is: {@code byte[]} or the class of a codec
 */
public interface NetworkChannelOf<D> extends MessageChannel {
    /** Receives the messages that servers send. */
    @FunctionalInterface
    interface Listener<D> {
        /**
         * Called on the proxy's event thread for every message from {@code from}. Keep it short and do not block;
         * an exception is logged and the other listeners still get the message.
         */
        void onMessage(@NotNull NetworkServer from, @NotNull D message);
    }

    /**
     * Sends {@code message} to {@code server}; returns {@code false} when the server has no player to carry it.
     *
     * @throws IllegalStateException when the channel is closed
     * @throws IllegalArgumentException when the message is longer than {@link #MAX_MESSAGE_BYTES}
     */
    boolean send(@NotNull NetworkServer server, @NotNull D message);

    /**
     * Sends {@code message} to every server that can take it and returns how many did.
     *
     * @throws IllegalStateException when the channel is closed
     */
    int broadcast(@NotNull D message);

    /**
     * Starts passing messages from servers to {@code listener}; adding the same listener twice has no effect. A
     * message a codec cannot read is logged and dropped.
     */
    void listen(@NotNull Listener<D> listener);

    /** Stops passing messages to {@code listener}. */
    void unlisten(@NotNull Listener<D> listener);
}
