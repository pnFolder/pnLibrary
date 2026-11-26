package org.pnfolder.pnlibrary.core.link.wire;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.core.link.remote.RemoteRequest;

/** A remote update request for another server or the proxy. */
public final class RequestFrame implements LinkFrame {
    private final RemoteRequest request;

    public RequestFrame(@NotNull RemoteRequest request) {
        this.request = request;
    }

    public @NotNull RemoteRequest getRequest() {
        return request;
    }
}
