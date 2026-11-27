package org.pnfolder.pnlibrary.api.network.message;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import org.jetbrains.annotations.NotNull;

/**
 * A codec whose bytes are deflated when that helps. A leading byte says which: {@code 0} the bytes as they are, {@code 1} deflated
 * (raw deflate without the zlib header and checksum). The inflated size is capped so a forged message cannot fill the memory.
 */
final class CompressedCodec<T> implements MessageCodec<T> {
    private static final int MAX_INFLATED_BYTES = 1 << 20;
    private static final int STORED = 0;
    private static final int DEFLATED = 1;

    private final MessageCodec<T> inner;

    CompressedCodec(MessageCodec<T> inner) {
        this.inner = inner;
    }

    @Override
    public void encode(@NotNull T message, @NotNull MessageWriter out) {
        byte[] raw = inner.toBytes(message);
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, true);
        try {
            deflater.setInput(raw);
            deflater.finish();
            ByteArrayOutputStream packed = new ByteArrayOutputStream();
            byte[] chunk = new byte[1024];
            while (!deflater.finished()) {
                packed.write(chunk, 0, deflater.deflate(chunk));
            }
            byte[] small = packed.toByteArray();
            if (small.length + 1 >= raw.length) {
                out.writeByte(STORED).writeBlock(raw);
                return;
            }
            out.writeByte(DEFLATED).writeVarInt(raw.length).writeBlock(small);
        } finally {
            deflater.end();
        }
    }

    @NotNull
    @Override
    public T decode(@NotNull MessageReader in) {
        if (in.readByte() == STORED) {
            return inner.fromBytes(in.readBlock());
        }
        int size = in.readVarInt();
        if (size < 0 || size > MAX_INFLATED_BYTES) {
            throw new IllegalArgumentException("Compressed message claims " + size + " bytes; the limit is " + MAX_INFLATED_BYTES);
        }
        byte[] block = in.readBlock();
        byte[] packed = java.util.Arrays.copyOf(block, block.length + 1); // raw deflate needs one extra byte
        Inflater inflater = new Inflater(true);
        try {
            inflater.setInput(packed);
            byte[] raw = new byte[size];
            int done = 0;
            while (done < size && !inflater.finished()) {
                int got = inflater.inflate(raw, done, size - done);
                if (got == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    break;
                }
                done += got;
            }
            if (done != size) {
                throw new IllegalArgumentException("Compressed message is damaged");
            }
            return inner.fromBytes(raw);
        } catch (DataFormatException error) {
            throw new IllegalArgumentException("Compressed message is damaged", error);
        } finally {
            inflater.end();
        }
    }
}
