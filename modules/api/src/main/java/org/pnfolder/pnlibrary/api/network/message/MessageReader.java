package org.pnfolder.pnlibrary.api.network.message;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Reads the data of one channel message in the order {@link MessageWriter} wrote it. A read past the end, or of the
 * wrong shape, throws {@link IllegalArgumentException}; the library logs it and goes on with the next message.
 *
 * <pre>{@code
 * channel.on("balance", (from, msg) -> {
 *     UUID id = msg.readUuid();
 *     int amount = msg.readInt();
 * });
 * }</pre>
 */
public final class MessageReader {
    private final String type;
    private final DataInputStream in;

    private MessageReader(String type, DataInputStream in) {
        this.type = type;
        this.in = in;
    }

    /** Reads the type of {@code data}; null when it is not a typed message, such as another plugin's raw bytes. */
    @Nullable
    public static MessageReader of(byte[] data) {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        try {
            return new MessageReader(in.readUTF(), in);
        } catch (IOException notTyped) {
            return null;
        }
    }

    /** A reader over {@code data} that holds no type. */
    public static MessageReader raw(byte[] data) {
        return new MessageReader("", new DataInputStream(new ByteArrayInputStream(data)));
    }

    /** The type the sender gave the message. */
    @NotNull
    public String getType() {
        return type;
    }

    /** Reads a text. */
    @NotNull
    public String readText() {
        return read(in::readUTF);
    }

    /** Reads a byte as written by {@link MessageWriter#writeByte(int)}, signed. */
    public byte readByte() {
        return read(in::readByte);
    }

    /** Reads a short as written by {@link MessageWriter#writeShort(int)}, signed. */
    public short readShort() {
        return read(in::readShort);
    }

    /** Reads a float. */
    public float readFloat() {
        return read(in::readFloat);
    }

    /** Reads a number written by {@link MessageWriter#writeVarInt(int)}. */
    public int readVarInt() {
        int value = 0;
        for (int shift = 0; shift < 35; shift += 7) {
            int next = readByte();
            value |= (next & 0x7F) << shift;
            if ((next & 0x80) == 0) {
                return value;
            }
        }
        throw new IllegalArgumentException("Message '" + type + "' holds a number that is too long");
    }

    /** Reads an int. */
    public int readInt() {
        return read(in::readInt);
    }

    /** Reads a long. */
    public long readLong() {
        return read(in::readLong);
    }

    /** Reads a double. */
    public double readDouble() {
        return read(in::readDouble);
    }

    /** Reads a boolean. */
    public boolean readBoolean() {
        return read(in::readBoolean);
    }

    /** Reads a UUID. */
    @NotNull
    public UUID readUuid() {
        return new UUID(readLong(), readLong());
    }

    /** Reads a block of bytes written with {@link MessageWriter#writeBlock(byte[])}. */
    @NotNull
    public byte[] readBlock() {
        int length = readInt();
        if (length < 0 || length > remaining()) {
            throw new IllegalArgumentException("Block of " + length + " bytes does not fit the message");
        }
        byte[] block = new byte[length];
        read(() -> {
            in.readFully(block);
            return null;
        });
        return block;
    }

    /** How many bytes are left to read. */
    public int remaining() {
        return read(in::available);
    }

    private <T> T read(IoRead<T> action) {
        try {
            return action.read();
        } catch (IOException error) {
            throw new IllegalArgumentException("Message '" + type + "' is shorter than expected", error);
        }
    }

    private interface IoRead<T> {
        T read() throws IOException;
    }
}
