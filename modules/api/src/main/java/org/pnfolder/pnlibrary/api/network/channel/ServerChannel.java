package org.pnfolder.pnlibrary.api.network.channel;

import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.network.channel.typed.ServerChannelOf;
import org.pnfolder.pnlibrary.api.network.message.MessageCodec;
import org.pnfolder.pnlibrary.api.network.message.MessageReader;
import org.pnfolder.pnlibrary.api.network.message.MessageWriter;

/**
 * A plugin-message channel on a game server, opened with {@link ServerNetwork#channel(String)}. The library registers
 * it with the server, picks a player to carry each message and receives what the proxy sends. The other end is the
 * {@link NetworkChannel} of the same name on the proxy.
 *
 * <pre>{@code
 * ServerChannel channel = library.getServerNetwork().channel(Key.key("myplugin:main"));
 * channel.listen(data -> handle(data));
 * channel.send(payload);
 * }</pre>
 */
public interface ServerChannel extends ServerChannelOf<byte[]> {
    /** Sends a message of {@code type}; {@code body} writes its data. Same as {@link #send(byte[])}. */
    default @NotNull CompletionStage<Boolean> send(@NotNull String type, @NotNull Consumer<MessageWriter> body) {
        return send(MessageWriter.encode(type, body));
    }

    /**
     * Passes the messages of {@code type} from the proxy to {@code listener}; other types and bytes that are not a
     * typed message are ignored. It lasts until the channel closes or the returned handle is run.
     *
     * @return a handle that stops this listener
     */
    default @NotNull Runnable on(@NotNull String type, @NotNull Consumer<MessageReader> listener) {
        Consumer<byte[]> raw = data -> {
            MessageReader message = MessageReader.of(data);
            if (message != null && message.getType().equals(type)) {
                listener.accept(message);
            }
        };
        listen(raw);
        return () -> unlisten(raw);
    }

    /**
     * A view of this channel that sends and receives objects of one class, converted by {@code codec}. Use the same
     * codec on the proxy and on the game server.
     */
    @NotNull <T> ServerChannelOf<T> with(@NotNull MessageCodec<T> codec);
}
