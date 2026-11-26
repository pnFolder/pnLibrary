package org.pnfolder.pnlibrary.core.link.wire;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.common.lang.Failures;
import org.pnfolder.pnlibrary.core.link.remote.DataChunk;

/** Cuts an answer into pieces that fit a plugin message and puts it back together. */
public final class DataChunks {
    /** The most bytes of one piece. */
    public static final int PIECE = 24_000;

    /** The most pieces of one answer. */
    public static final int MAX_PIECES = 80;

    private static final int MAX_INFLATED = 4_000_000;
    private static final int BUFFER = 4_096;

    private DataChunks() {
    }

    /** The signed messages that carry {@code text} to {@code to}, or null when it is too long even for {@link #MAX_PIECES}. */
    public static @Nullable List<byte[]> split(@NotNull UUID id, @NotNull String to, boolean ok, @NotNull String text,
                                               @NotNull byte[] key) {
        return split(id, to, ok, text, key, Instant.now());
    }

    /** The signed messages that carry {@code text} to {@code to}, or null when it is too long even for {@link #MAX_PIECES}. */
    public static @Nullable List<byte[]> split(@NotNull UUID id, @NotNull String to, boolean ok, @NotNull String text,
                                               @NotNull byte[] key, @NotNull Instant now) {
        byte[] bytes = deflate(text);
        int count = Math.max(1, (bytes.length + PIECE - 1) / PIECE);
        if (count > MAX_PIECES) {
            return null;
        }
        List<byte[]> messages = new ArrayList<byte[]>(count);
        for (int index = 0; index < count; index++) {
            byte[] piece = Arrays.copyOfRange(bytes, index * PIECE, Math.min(bytes.length, (index + 1) * PIECE));
            messages.add(LinkCodec.encodeChunk(new DataChunk(id, to, ok, index, count, piece, now), key));
        }
        return messages;
    }

    /** The text in {@code pieces}, which must be all of them in order, or null when they do not hold one. */
    public static @Nullable String join(@NotNull List<byte[]> pieces) {
        return Failures.runCatchingNonFatal(() -> inflate(pieces)).getOrNull();
    }

    private static byte[] deflate(String text) {
        ByteArrayOutputStream packed = new ByteArrayOutputStream();
        try (DeflaterOutputStream out = new DeflaterOutputStream(packed)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
        return packed.toByteArray();
    }

    private static String inflate(List<byte[]> pieces) throws IOException {
        ByteArrayOutputStream all = new ByteArrayOutputStream();
        for (byte[] piece : pieces) {
            all.write(piece);
        }
        try (InflaterInputStream input = new InflaterInputStream(new ByteArrayInputStream(all.toByteArray()))) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[BUFFER];
            while (true) {
                int read = input.read(buffer);
                if (read < 0) {
                    return new String(out.toByteArray(), StandardCharsets.UTF_8);
                }
                out.write(buffer, 0, read);
                if (out.size() > MAX_INFLATED) {
                    return null;
                }
            }
        }
    }
}
