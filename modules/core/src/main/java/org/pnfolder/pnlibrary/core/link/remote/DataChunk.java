package org.pnfolder.pnlibrary.core.link.remote;

import java.time.Instant;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;

/** One piece of the answer to a {@link DataCall}. An answer can be longer than a plugin message, so it travels in pieces. */
public final class DataChunk {
    private final UUID id;
    private final String to;
    private final boolean ok;
    private final int index;
    private final int count;
    private final byte[] bytes;
    private final Instant sentAt;

    /**
     * @param id the id of the question answered
     * @param to the server that asked
     * @param ok whether the answer is the data, or the reason there is none
     * @param index the place of this piece, from 0
     * @param count how many pieces the answer has
     * @param bytes the compressed text of this piece
     * @param sentAt when the piece was made
     */
    public DataChunk(@NotNull UUID id, @NotNull String to, boolean ok, int index, int count, @NotNull byte[] bytes,
                     @NotNull Instant sentAt) {
        this.id = id;
        this.to = to;
        this.ok = ok;
        this.index = index;
        this.count = count;
        this.bytes = bytes;
        this.sentAt = sentAt;
    }

    public @NotNull UUID getId() {
        return id;
    }

    public @NotNull String getTo() {
        return to;
    }

    public boolean getOk() {
        return ok;
    }

    public int getIndex() {
        return index;
    }

    public int getCount() {
        return count;
    }

    public @NotNull byte[] getBytes() {
        return bytes;
    }

    public @NotNull Instant getSentAt() {
        return sentAt;
    }
}
