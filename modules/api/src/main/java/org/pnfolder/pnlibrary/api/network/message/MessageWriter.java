package org.pnfolder.pnlibrary.api.network.message;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;

/**
 * Writes the data of one channel message. Read it back in the same order with {@link MessageReader}.
 *
 * <pre>{@code
 * channel.send("balance", msg -> msg.writeUuid(id).writeInt(250));
 * }</pre>
 */
public final class MessageWriter {
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final DataOutputStream out = new DataOutputStream(bytes);

    MessageWriter() {
    }

    /** The bytes of a message: its {@code type} first, then what {@code body} writes. */
    public static byte[] encode(String type, Consumer<MessageWriter> body) {
        MessageWriter writer = new MessageWriter();
        writer.writeText(type);
        body.accept(writer);
        return writer.bytes.toByteArray();
    }

    /** The bytes of a message without a type: what {@code body} writes. */
    public static byte[] encodeRaw(Consumer<MessageWriter> body) {
        MessageWriter writer = new MessageWriter();
        body.accept(writer);
        return writer.bytes.toByteArray();
    }

    /** Writes a text of up to 65535 bytes. */
    @NotNull
    public MessageWriter writeText(@NotNull String value) {
        try {
            out.writeUTF(value);
        } catch (IOException error) {
            throw new IllegalArgumentException("Text does not fit a message: " + error.getMessage(), error);
        }
        return this;
    }

    /** Writes the low 8 bits of {@code value}: 1 byte. */
    @NotNull
    public MessageWriter writeByte(int value) {
        return run(() -> out.writeByte(value));
    }

    /** Writes the low 16 bits of {@code value}: 2 bytes. */
    @NotNull
    public MessageWriter writeShort(int value) {
        return run(() -> out.writeShort(value));
    }

    /** Writes a float: 4 bytes, about 7 digits, half of a double. */
    @NotNull
    public MessageWriter writeFloat(float value) {
        return run(() -> out.writeFloat(value));
    }

    /**
     * Writes a non-negative int in 1 to 5 bytes: 1 byte below 128, 2 below 16384, 3 below 2097152. A negative number
     * takes 5 bytes, so use {@link #writeInt(int)} for those.
     */
    @NotNull
    public MessageWriter writeVarInt(int value) {
        int rest = value;
        while ((rest & ~0x7F) != 0) {
            writeByte((rest & 0x7F) | 0x80);
            rest >>>= 7;
        }
        return writeByte(rest);
    }

    /** Writes an int. */
    @NotNull
    public MessageWriter writeInt(int value) {
        return run(() -> out.writeInt(value));
    }

    /** Writes a long. */
    @NotNull
    public MessageWriter writeLong(long value) {
        return run(() -> out.writeLong(value));
    }

    /** Writes a double. */
    @NotNull
    public MessageWriter writeDouble(double value) {
        return run(() -> out.writeDouble(value));
    }

    /** Writes a boolean. */
    @NotNull
    public MessageWriter writeBoolean(boolean value) {
        return run(() -> out.writeBoolean(value));
    }

    /** Writes a UUID. */
    @NotNull
    public MessageWriter writeUuid(@NotNull UUID value) {
        return writeLong(value.getMostSignificantBits()).writeLong(value.getLeastSignificantBits());
    }

    /** Writes a block of bytes together with its length. */
    @NotNull
    public MessageWriter writeBlock(@NotNull byte[] value) {
        writeInt(value.length);
        return run(() -> out.write(value));
    }

    private MessageWriter run(IoAction action) {
        try {
            action.run();
        } catch (IOException error) {
            throw new IllegalStateException("Writing to memory cannot fail", error);
        }
        return this;
    }

    private interface IoAction {
        void run() throws IOException;
    }
}
