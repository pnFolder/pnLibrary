package org.pnfolder.pnlibrary.api.network.channel.typed;

import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.network.channel.MessageChannel;
import org.pnfolder.pnlibrary.api.network.channel.ServerChannel;
import org.pnfolder.pnlibrary.api.network.message.MessageCodec;

/**
 * A game-server channel that carries messages of one class {@code D}. {@link ServerChannel} is the one for
 * {@code byte[]}; {@link ServerChannel#with(MessageCodec)} gives one for your own class.
 *
 * @param <D> what a message is: {@code byte[]} or the class of a codec
 */
public interface ServerChannelOf<D> extends MessageChannel {
    /**
     * Sends {@code message} to the proxy. A message needs a player online to travel, so with nobody online it waits
     * and goes out when the first player joins. At most 100 messages wait per channel; the oldest is dropped first.
     *
     * @return {@code true} once the message left the server, {@code false} if it was dropped or the channel closed
     * @throws IllegalStateException when the channel is closed
     * @throws IllegalArgumentException when the message is longer than {@link #MAX_MESSAGE_BYTES}
     */
    @NotNull CompletionStage<Boolean> send(@NotNull D message);

    /**
     * Starts passing messages from the proxy to {@code listener}; adding the same listener twice has no effect. A
     * message a codec cannot read is logged and dropped.
     */
    void listen(@NotNull Consumer<D> listener);

    /** Stops passing messages to {@code listener}. */
    void unlisten(@NotNull Consumer<D> listener);
}
