package org.pnfolder.pnlibrary.core.link.wire;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.core.link.remote.DataChunk;

/** One piece of the answer to a data question. */
public final class ChunkFrame implements LinkFrame {
    private final DataChunk chunk;

    public ChunkFrame(@NotNull DataChunk chunk) {
        this.chunk = chunk;
    }

    public @NotNull DataChunk getChunk() {
        return chunk;
    }
}
