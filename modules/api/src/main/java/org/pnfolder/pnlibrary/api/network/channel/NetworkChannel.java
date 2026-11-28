package org.pnfolder.pnlibrary.api.network.channel;

import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.network.NetworkProxy;
import org.pnfolder.pnlibrary.api.network.NetworkServer;
import org.pnfolder.pnlibrary.api.network.channel.typed.MessageListener;
import org.pnfolder.pnlibrary.api.network.channel.typed.NetworkChannelOf;
import org.pnfolder.pnlibrary.api.network.message.MessageCodec;
import org.pnfolder.pnlibrary.api.network.message.MessageReader;
import org.pnfolder.pnlibrary.api.network.message.MessageWriter;

/**
 * A plugin-message channel of the proxy, opened with {@link NetworkProxy#channel(Key)}. Messages from backend
 * servers go to the listeners and never on to the players; messages from players are ignored, since a client can
 * send anything on a channel.
 *
 * <pre>{@code
 * NetworkChannel channel = library.getProxy().channel(Key.key("myplugin:main"));
 * channel.listen((from, data) -> handle(from.getName(), data));   // data is a byte[]
 * channel.send(library.getProxy().server("lobby"), payload);
 * }</pre>
 */
public interface NetworkChannel extends NetworkChannelOf<byte[]> {
    /** Sends a message of {@code type}; {@code body} writes its data. Same as {@code send(NetworkServer, byte[])}. */
    default boolean send(@NotNull NetworkServer server, @NotNull String type, @NotNull Consumer<MessageWriter> body) {
        return send(server, MessageWriter.encode(type, body));
    }

    /** Broadcasts a message of {@code type}; {@code body} writes its data. Same as {@code broadcast(byte[])}. */
    default int broadcast(@NotNull String type, @NotNull Consumer<MessageWriter> body) {
        return broadcast(MessageWriter.encode(type, body));
    }

    /**
     * Passes the messages of {@code type} from servers to {@code listener}; other types and bytes that are not a typed
     * message are ignored. It lasts until the channel closes or the returned handle is run.
     *
     * @return a handle that stops this listener
     */
    default @NotNull Runnable on(@NotNull String type, @NotNull MessageListener listener) {
        Listener<byte[]> raw = (from, data) -> {
            MessageReader message = MessageReader.of(data);
            if (message != null && message.getType().equals(type)) {
                listener.onMessage(from, message);
            }
        };
        listen(raw);
        return () -> unlisten(raw);
    }

    /**
     * A view of this channel that sends and receives objects of one class, converted by {@code codec}. Use the same
     * codec on the proxy and on the game server and give it only to channels both sides agree on.
     */
    @NotNull <T> NetworkChannelOf<T> with(@NotNull MessageCodec<T> codec);
}
