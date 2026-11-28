package org.pnfolder.pnlibrary.api.network.message;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.network.channel.MessageChannel;
import org.pnfolder.pnlibrary.api.network.channel.typed.ServerChannelOf;

/**
 * Turns your class into the bytes of a channel message and back. Write one codec, put it where the proxy plugin and
 * the game-server plugin both see it, and give it to the channel: you send and receive the class itself.
 *
 * <pre>{@code
 * public final class BalanceCodec implements MessageCodec<Balance> {
 *     @Override
 *     public void encode(Balance message, MessageWriter out) {
 *         out.writeUuid(message.id).writeInt(message.amount);
 *     }
 *
 *     @Override
 *     public Balance decode(MessageReader in) {                  // same order as encode
 *         return new Balance(in.readUuid(), in.readInt());
 *     }
 * }
 *
 * ServerChannelOf<Balance> channel = library.getServerNetwork().channel(Key.key("myplugin:main")).with(new BalanceCodec());
 * channel.send(new Balance(id, 250));
 * }</pre>
 *
 * @param <T> the class this codec carries
 */
public interface MessageCodec<T> {
    /**
     * Wraps {@code codec} so that the bytes it writes are deflated before they leave and inflated when they arrive.
     * It pays off for long lists with repeated parts such as names, worlds or small numbers; a few random bytes such
     * as UUIDs hardly shrink. Both sides must wrap the codec the same way.
     */
    static <T> @NotNull MessageCodec<T> compressed(@NotNull MessageCodec<T> codec) {
        return new CompressedCodec<>(codec);
    }

    /**
     * A codec for a list of {@code T}: the length, then the items one after another. Use it for a message that is a
     * whole list, and {@link #split(List)} when the list may not fit in one message.
     */
    static <T> @NotNull MessageCodec<List<T>> listOf(@NotNull MessageCodec<T> item) {
        return new ListCodec<>(item);
    }

    /** Writes {@code message}; {@link #decode(MessageReader)} reads it back in the same order. */
    void encode(@NotNull T message, @NotNull MessageWriter out);

    /** Builds the message from what {@link #encode(Object, MessageWriter)} wrote. */
    @NotNull T decode(@NotNull MessageReader in);

    /** The bytes of {@code message}; the library calls this when sending. */
    default byte[] toBytes(@NotNull T message) {
        return MessageWriter.encodeRaw(out -> encode(message, out));
    }

    /**
     * The message in {@code data}; the library calls this when receiving.
     *
     * @throws IllegalArgumentException when {@code data} is shorter than the codec reads
     */
    default @NotNull T fromBytes(@NotNull byte[] data) {
        return decode(MessageReader.raw(data));
    }

    /**
     * Cuts {@code items} into parts that each fit in one message when sent as a {@link #listOf(MessageCodec) list}.
     * It measures every item with this codec, so it suits codecs that write one item after another (not columns).
     * Wrapping the list codec in {@link #compressed(MessageCodec)} only makes the parts smaller.
     *
     * <pre>{@code
     * MessageCodec<List<Row>> list = MessageCodec.listOf(rowCodec);
     * for (List<Row> part : rowCodec.split(rows)) {
     *     channel.send(server, part);   // channel = ...with(list)
     * }
     * }</pre>
     *
     * @throws IllegalArgumentException when one item alone is too big for a message
     */
    default @NotNull List<List<T>> split(@NotNull List<T> items) {
        int budget = MessageChannel.MAX_MESSAGE_BYTES - 16; // room for the length and a compression marker
        List<List<T>> parts = new ArrayList<>();
        List<T> part = new ArrayList<>();
        int size = 0;
        for (T next : items) {
            int bytes = toBytes(next).length;
            if (bytes > budget) {
                throw new IllegalArgumentException("One item takes " + bytes + " bytes; a message holds " + budget);
            }
            if (size + bytes > budget) {
                parts.add(part);
                part = new ArrayList<>();
                size = 0;
            }
            part.add(next);
            size += bytes;
        }
        if (!part.isEmpty() || parts.isEmpty()) {
            parts.add(part);
        }
        return parts;
    }
}
