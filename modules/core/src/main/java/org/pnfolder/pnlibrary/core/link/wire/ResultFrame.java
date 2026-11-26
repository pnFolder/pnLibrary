package org.pnfolder.pnlibrary.core.link.wire;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.core.link.remote.RemoteResult;

/** The answer to a remote update request. */
public final class ResultFrame implements LinkFrame {
    private final RemoteResult result;

    public ResultFrame(@NotNull RemoteResult result) {
        this.result = Objects.requireNonNull(result, "result");
    }

    public @NotNull RemoteResult getResult() {
        return result;
    }
}
