package org.pnfolder.pnlibrary.core.link.wire.frame;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.core.link.remote.DataCall;

/** A data question for another server or the proxy. */
public final class CallFrame implements LinkFrame {
    private final DataCall call;

    public CallFrame(@NotNull DataCall call) {
        this.call = Objects.requireNonNull(call, "call");
    }

    public @NotNull DataCall getCall() {
        return call;
    }
}
