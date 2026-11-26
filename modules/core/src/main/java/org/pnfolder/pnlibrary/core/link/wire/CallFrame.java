package org.pnfolder.pnlibrary.core.link.wire;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.core.link.remote.DataCall;

/** A data question for another server or the proxy. */
public final class CallFrame implements LinkFrame {
    private final DataCall call;

    public CallFrame(@NotNull DataCall call) {
        this.call = call;
    }

    public @NotNull DataCall getCall() {
        return call;
    }
}
